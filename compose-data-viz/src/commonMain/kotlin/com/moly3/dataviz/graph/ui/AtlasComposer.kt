package com.moly3.dataviz.graph.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.graph.features.atlas.AtlasLayers
import com.moly3.dataviz.graph.features.atlas.AtlasState
import com.moly3.dataviz.graph.features.atlas.AtlasTier
import com.moly3.dataviz.graph.features.atlas.func.createSvgAtlas
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Clock

private const val DEFAULT_IDLE_MILLIS = 150L
private const val DEFAULT_VISIBILITY_PADDING_PX = 50f
private const val DEFAULT_CONCURRENCY_LIMIT = 15
private const val MOVEMENT_COMPUTE_THROTTLE_MILLIS = 100L
private const val MAX_SCORING_NODES = 256
private const val DEFAULT_MAX_FALLBACK_NODES = 60
private const val DEFAULT_TILE_SIZE = 64

/** How a tier picks which nodes to include. */
sealed interface TierSelection {
    data object All : TierSelection

    data object AllVisible : TierSelection

    data class TopByDistance(
        val count: Int,
    ) : TierSelection
}

typealias AtlasPainterLoader<Id, Data> = suspend (GraphNode<Id, Data>) -> Painter?

@Stable
class AtlasComposerHandle<Id, Data> internal constructor(
    private val state: AtlasComposerState<Id, Data>,
) {
    val atlasLayers: AtlasLayers get() = state.atlasLayers

    fun resolveIconKey(
        nodeId: Id,
        data: Data?,
    ): String? = state.resolveIconKey(nodeId, data)
}

// =====================================================================
//  Movement helper
// =====================================================================

/**
 * Tracks transient movement with an idle timeout. Call [trigger] every time
 * something moves (pan, zoom, velocity tick); [isMoving] stays `true` until
 * [idleMillis] elapse with no further [trigger] calls, then flips to `false`.
 *
 * Pass [isMoving] straight into `rememberAtlasComposer`'s `isMoving` param.
 */
@Stable
class MovementTracker internal constructor(
    private val scope: CoroutineScope,
    private val idleMillis: Long,
) {
    private val _isMoving = mutableStateOf(false)
    val isMoving: Boolean get() = _isMoving.value
    val isMovingState: State<Boolean> get() = _isMoving

    private var idleJob: Job? = null

    fun trigger() {
        _isMoving.value = true
        idleJob?.cancel()
        idleJob =
            scope.launch {
                delay(idleMillis)
                _isMoving.value = false
            }
    }

    fun stopImmediately() {
        idleJob?.cancel()
        idleJob = null
        _isMoving.value = false
    }
}

@Composable
fun rememberMovementTracker(idleMillis: Long = DEFAULT_IDLE_MILLIS): MovementTracker {
    val scope = rememberCoroutineScope()
    return remember(scope, idleMillis) { MovementTracker(scope, idleMillis) }
}

// =====================================================================
//  Atlas composer
// =====================================================================

/**
 * Build a tiered atlas. Painters are produced by [loader] (suspend, opaque to
 * the composer — it knows nothing about how painters are obtained). All atlas
 * construction runs on [Dispatchers.Default]; composition is never blocked.
 *
 * Tier names must be unique and non-blank — they are the cache key for tier
 * atlases. Two tiers with the same name will fight over the same slot.
 */
@Composable
fun <Id, Data> rememberAtlasComposer(
    nodes: List<GraphNode<Id, Data>>,
    tiers: List<AtlasTier>,
    viewport: IntSize,
    userPosition: Offset,
    zoom: Float,
    coordinates: Map<Id, Offset>,
    loader: AtlasPainterLoader<Id, Data>,
    loaderKey: Any = Unit,
    staticIcons: ImmutableMap<String, Painter> = persistentMapOf(),
    staticIconKey: (Id, Data?) -> String? = { _, _ -> null },
    visibilityPaddingPx: Float = DEFAULT_VISIBILITY_PADDING_PX,
    isMoving: Boolean = false,
    concurrencyLimit: Int = DEFAULT_CONCURRENCY_LIMIT,
): AtlasComposerHandle<Id, Data> {
    require(tiers.map { it.name }.toSet().size == tiers.size && tiers.none { it.name.isBlank() }) {
        "AtlasTier names must be unique and non-blank. Got: ${tiers.map { it.name }}"
    }

    val density = LocalDensity.current
    val state = remember(density) { AtlasComposerState<Id, Data>(density) }

    if (state.nodes !== nodes) state.nodes = nodes
    if (state.tiers !== tiers) state.tiers = tiers
    if (state.staticIcons !== staticIcons) state.staticIcons = staticIcons
    state.staticIconKey = staticIconKey
    state.visibilityPaddingPx = visibilityPaddingPx
    state.loader = loader
    state.concurrencyLimit = concurrencyLimit
    if (state.userPosition != userPosition) state.userPosition = userPosition
    if (state.zoom != zoom) state.zoom = zoom
    if (state.viewport != viewport) state.viewport = viewport
    if (state.coordinates !== coordinates) state.coordinates = coordinates
    state.isMoving = isMoving

    LaunchedEffect(state, loaderKey) { state.invalidatePainterCache() }

    LaunchedEffect(state) {
        var lastComputeTime = 0L
        snapshotFlow {
            VisibilityInputs(
                state.userPosition,
                state.zoom,
                state.viewport,
                state.coordinates,
                state.nodes.size,
                state.isMoving,
            )
        }.collect { inputs ->
            val now = Clock.System.now().toEpochMilliseconds()
            if (!inputs.isMoving) {
                state.recomputeVisibility()
                lastComputeTime = now
            } else if (now - lastComputeTime > MOVEMENT_COMPUTE_THROTTLE_MILLIS) {
                state.recomputeVisibility()
                lastComputeTime = now
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.candidateIdsForLoad() }.collect { ids ->
            state.ensurePaintersLoaded(ids, this)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.atlasBuildInputs() }.collect { inputs ->
            state.rebuildTierAtlases(inputs)
        }
    }

    return remember(state) { AtlasComposerHandle(state) }
}

// =====================================================================
//  Internal state
// =====================================================================

@Stable
internal class AtlasComposerState<Id, Data>(
    private val density: Density,
) {
    var nodes by mutableStateOf<List<GraphNode<Id, Data>>>(emptyList())
    var isMoving by mutableStateOf(false)
    var tiers by mutableStateOf<List<AtlasTier>>(emptyList())
    var staticIcons by mutableStateOf<ImmutableMap<String, Painter>>(persistentMapOf())
    var staticIconKey: (Id, Data?) -> String? = { _, _ -> null }
    var loader: AtlasPainterLoader<Id, Data>? = null
    var visibilityPaddingPx: Float = DEFAULT_VISIBILITY_PADDING_PX
    var concurrencyLimit: Int = DEFAULT_CONCURRENCY_LIMIT

    var userPosition by mutableStateOf(Offset.Zero)
    var zoom by mutableStateOf(1f)
    var viewport by mutableStateOf(IntSize.Zero)
    var coordinates by mutableStateOf<Map<Id, Offset>>(emptyMap())

    private var visibleNodeIds by mutableStateOf<ImmutableList<Id>>(persistentListOf())
    private var composableVisibleByDistance by mutableStateOf<ImmutableList<Id>>(persistentListOf())

    private val loadedPainters = mutableStateMapOf<Id, Painter?>()
    private val inFlight = HashSet<Id>()
    private val loadSemaphore by lazy { Semaphore(concurrencyLimit) }

    private val tierAtlases = mutableStateMapOf<String, AtlasState>()
    private val tierBuildSignatures = HashMap<String, TierAtlasSignature>()

    private fun classifyNode(node: GraphNode<Id, Data>): NodeIconKind {
        val key = staticIconKey(node.id, node.data) ?: return NodeIconKind.ColorOnly
        return if (staticIcons.containsKey(key)) {
            NodeIconKind.Static(key)
        } else {
            NodeIconKind.Composable
        }
    }

    fun recomputeVisibility() {
        val size = viewport
        if (size.width <= 0) return
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val pad = visibilityPaddingPx

        val scored = ArrayList<Pair<Id, Float>>(nodes.size.coerceAtMost(MAX_SCORING_NODES))
        for (node in nodes) {
            val coord = coordinates[node.id] ?: continue
            val sx = (coord.x + userPosition.x) * zoom + w / 2f
            val sy = (coord.y + userPosition.y) * zoom + h / 2f
            if (sx < -pad || sx > w + pad || sy < -pad || sy > h + pad) continue
            val dx = (coord.x + userPosition.x) * zoom
            val dy = (coord.y + userPosition.y) * zoom
            scored.add(node.id to dx * dx + dy * dy)
        }
        scored.sortBy { it.second }

        val ids: ImmutableList<Id> =
            if (scored.isEmpty() && nodes.isNotEmpty()) {
                val maxFallback =
                    (
                        tiers.maxOfOrNull {
                            when (val sel = it.selection) {
                                TierSelection.All, TierSelection.AllVisible -> DEFAULT_MAX_FALLBACK_NODES
                                is TierSelection.TopByDistance -> sel.count
                            }
                        } ?: DEFAULT_MAX_FALLBACK_NODES
                    ).coerceAtLeast(1)
                nodes
                    .asSequence()
                    .take(maxFallback)
                    .map { it.id }
                    .toList()
                    .toImmutableList()
            } else {
                scored.map { it.first }.toImmutableList()
            }

        if (ids != visibleNodeIds) visibleNodeIds = ids

        val nodesById = nodes.associateBy { it.id }
        val composables = ArrayList<Id>(ids.size)
        for (id in ids) {
            val node = nodesById[id] ?: continue
            if (classifyNode(node) is NodeIconKind.Composable) composables += id
        }
        val composableList = composables.toImmutableList()
        if (composableList != composableVisibleByDistance) {
            composableVisibleByDistance = composableList
        }

        val needAllNodes = tiers.any { it.selection is TierSelection.All }
        val keep: HashSet<Id> =
            if (needAllNodes) {
                HashSet<Id>(nodes.size).also { set -> nodes.forEach { set.add(it.id) } }
            } else {
                ids.toHashSet()
            }
        loadedPainters.keys.retainAll(keep)
    }

    fun invalidatePainterCache() {
        loadedPainters.clear()
        inFlight.clear()
        tierAtlases.clear()
        tierBuildSignatures.clear()
    }

    fun candidateIdsForLoad(): ImmutableList<Id> {
        val hasAllTier = tiers.any { it.selection is TierSelection.All }
        val out = ArrayList<Id>()
        if (hasAllTier) {
            for (node in nodes) {
                if (classifyNode(node) is NodeIconKind.Composable) out += node.id
            }
        } else {
            val nodesById = nodes.associateBy { it.id }
            for (id in visibleNodeIds) {
                val node = nodesById[id] ?: continue
                if (classifyNode(node) is NodeIconKind.Composable) out += id
            }
        }
        return out.toImmutableList()
    }

    suspend fun ensurePaintersLoaded(
        ids: List<Id>,
        scope: CoroutineScope,
    ) {
        val ldr = loader ?: return
        val nodesById = nodes.associateBy { it.id }
        for (id in ids) {
            if (loadedPainters.containsKey(id)) continue
            if (!inFlight.add(id)) continue
            val node =
                nodesById[id] ?: run {
                    inFlight.remove(id)
                    continue
                }
            scope.launch {
                val painter: Painter? =
                    try {
                        loadSemaphore.withPermit { ldr(node) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                loadedPainters[id] = painter
                inFlight.remove(id)
            }
        }
    }

    data class AtlasBuildInputs<Id>(
        val tiers: List<AtlasTier>,
        val composableVisible: ImmutableList<Id>,
        val nodeIdsForAll: List<Id>,
        val loadedSnapshot: Map<Id, Painter?>,
        val isMoving: Boolean,
    )

    fun atlasBuildInputs(): AtlasBuildInputs<Id> {
        val hasAllTier = tiers.any { it.selection is TierSelection.All }
        val allIds: List<Id> =
            if (hasAllTier) {
                val out = ArrayList<Id>(nodes.size)
                for (node in nodes) {
                    if (classifyNode(node) is NodeIconKind.Composable) out += node.id
                }
                out
            } else {
                emptyList()
            }
        return AtlasBuildInputs(
            tiers = tiers,
            composableVisible = composableVisibleByDistance,
            nodeIdsForAll = allIds,
            loadedSnapshot = loadedPainters.toMap(),
            isMoving = isMoving,
        )
    }

    suspend fun rebuildTierAtlases(inputs: AtlasBuildInputs<Id>) {
        for (tier in inputs.tiers) {
            if (inputs.isMoving && tier.freezeOnMove) continue

            val candidates: List<Id> =
                when (val sel = tier.selection) {
                    TierSelection.All -> inputs.nodeIdsForAll
                    TierSelection.AllVisible -> inputs.composableVisible
                    is TierSelection.TopByDistance -> inputs.composableVisible.take(sel.count)
                }

            val selected = ArrayList<Id>(candidates.size)
            val painters = ArrayList<Painter>(candidates.size)
            for (id in candidates) {
                val p = inputs.loadedSnapshot[id] ?: continue
                selected += id
                painters += p
            }
            if (painters.isEmpty()) {
                tierAtlases.remove(tier.name)
                tierBuildSignatures.remove(tier.name)
                continue
            }

            val signature =
                TierAtlasSignature(
                    ids = selected.toList(),
                    painterIdentities = painters.toList(),
                    tileSizePx = tier.tileSizePx,
                    isCircular = tier.isCircular,
                )

            if (tierBuildSignatures[tier.name] == signature) continue

            val atlas =
                withContext(Dispatchers.Default) {
                    val result = createSvgAtlas(painters, density, tier.tileSizePx)
                    val indexes = HashMap<String, Int>(selected.size)
                    for ((i, id) in selected.withIndex()) indexes[nodeKey(id)] = i
                    AtlasState(
                        bitmap = result.imageBitmap,
                        indexMap = indexes.toPersistentMap(),
                        columns = result.columns,
                        tileSizePx = result.tileSizePx,
                        isCircular = tier.isCircular,
                        version = Clock.System.now().toEpochMilliseconds(),
                    )
                }

            tierAtlases[tier.name] = atlas
            tierBuildSignatures[tier.name] = signature
        }

        // Drop entries for tiers that no longer exist.
        val liveTierNames = inputs.tiers.map { it.name }.toHashSet()
        tierBuildSignatures.keys.retainAll(liveTierNames)
        tierAtlases.keys.retainAll(liveTierNames)
    }

    private var cachedStaticAtlas: Pair<ImmutableMap<String, Painter>, AtlasState>? = null

    private fun buildStaticAtlas(): AtlasState? {
        if (staticIcons.isEmpty()) return null
        cachedStaticAtlas?.let { (cachedIcons, atlas) ->
            if (cachedIcons === staticIcons) return atlas
        }
        val keys = staticIcons.keys.toList()
        val painters = keys.map { staticIcons.getValue(it) }
        val indexes = keys.mapIndexed { i, k -> k to i }.toMap()
        val tileSize = tiers.maxOfOrNull { it.tileSizePx } ?: DEFAULT_TILE_SIZE
        val result = createSvgAtlas(painters, density, tileSize)
        val atlas =
            AtlasState(
                bitmap = result.imageBitmap,
                indexMap = indexes.toPersistentMap(),
                columns = result.columns,
                tileSizePx = result.tileSizePx,
                isCircular = true,
                version = 0L,
            )
        cachedStaticAtlas = staticIcons to atlas
        return atlas
    }

    val atlasLayers: AtlasLayers by derivedStateOf {
        val tierLayers = tiers.mapNotNull { tierAtlases[it.name] }
        val staticLayer = buildStaticAtlas()
        val all =
            buildList {
                addAll(tierLayers)
                staticLayer?.let { add(it) }
            }
        if (all.isEmpty()) AtlasLayers.EMPTY else AtlasLayers(all.toPersistentList())
    }

    fun resolveIconKey(
        nodeId: Id,
        data: Data?,
    ): String? {
        val userKey = staticIconKey(nodeId, data) ?: return null
        if (staticIcons.containsKey(userKey)) {
            return if (atlasLayers.resolve(userKey) != null) userKey else null
        }
        val key = nodeKey(nodeId)
        return if (atlasLayers.resolve(key) != null) key else null
    }

    private fun nodeKey(id: Id): String = "node:$id"
}

private data class TierAtlasSignature(
    val ids: List<Any?>,
    val painterIdentities: List<Painter>,
    val tileSizePx: Int,
    val isCircular: Boolean,
)

private sealed interface NodeIconKind {
    data object ColorOnly : NodeIconKind

    data class Static(
        val key: String,
    ) : NodeIconKind

    data object Composable : NodeIconKind
}

private data class VisibilityInputs(
    val offset: Offset,
    val zoom: Float,
    val viewport: IntSize,
    val coordinates: Map<*, Offset>,
    val nodeCount: Int,
    val isMoving: Boolean,
)
