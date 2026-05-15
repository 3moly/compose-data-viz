package com.moly3.dataviz.graph.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.func.rememberPainterFromComposable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.yield
import kotlin.time.Clock

// =====================================================================
//  Public API
// =====================================================================

/**
 * One quality tier of the atlas pyramid.
 *
 * Tiers are independent atlases stacked into [AtlasLayers]. The graph engine
 * resolves an icon key against layers in declared order — put your highest-
 * quality tier first to make it win when it has the key.
 *
 * Typical setup:
 * ```
 * listOf(
 *   AtlasTier("hq", tileSizePx = 128, selection = TopByDistance(12)),
 *   AtlasTier("lq", tileSizePx = 32,  selection = AllVisible),
 * )
 * ```
 */
@Immutable
data class AtlasTier(
    val name: String,
    val tileSizePx: Int,
    val selection: TierSelection,
    val isCircular: Boolean = false,
)

/** How a tier picks which nodes to include. */
sealed interface TierSelection {
    /** Include every node in the input list — visible or not. Use for full pre-bake. */
    data object All : TierSelection

    /** Include every currently-visible node. Cheap, broad LQ fallback. */
    data object AllVisible : TierSelection

    /** Include only the [count] closest visible nodes (by squared screen distance to center). */
    data class TopByDistance(val count: Int) : TierSelection
}

/**
 * Receiver of per-node content lambdas. Use [setReady] to signal that async
 * content has finished loading and is safe to record into the atlas. Sync
 * content can ignore this — the composer captures after the first frame.
 */
@Stable
interface CaptureScope {
    val isReadySignaled: Boolean
    fun setReady(ready: Boolean)
}

/**
 * Pass [atlasLayers] to Graph and call [resolveIconKey] inside `Graph`'s
 * `getIconKey` callback. Call [MountCaptureHolders] once in the same
 * composition (e.g. just above Graph).
 */
@Stable
class AtlasComposerHandle<Id, Data> internal constructor(
    private val state: AtlasComposerState<Id, Data>
) {
    val atlasLayers: AtlasLayers get() = state.atlasLayers
    fun resolveIconKey(nodeId: Id, data: Data?): String? = state.resolveIconKey(nodeId, data)

    @Composable
    fun MountCaptureHolders() = state.MountCaptureHolders()
}

/**
 * Build a tiered atlas from per-node composable content.
 *
 * Icon-source classification (per node) is driven by [staticIconKey]:
 *  - returns `null`         → color-only node, no icon, no capture
 *  - returns key in [staticIcons] → use that bundled static icon
 *  - returns key NOT in [staticIcons] → render [content] for the node and
 *    record it into the tier atlases under that key
 *
 * @param nodes the full node list (composer culls to viewport internally,
 *   except for tiers using [TierSelection.All]).
 * @param tiers ordered quality tiers, highest priority first.
 * @param viewport current laid-out size; pass [IntSize.Zero] until measured.
 * @param userPosition the graph's pan offset.
 * @param zoom the graph's zoom.
 * @param coordinates current node positions.
 * @param staticIcons keyed painters always present in a bottom-priority layer.
 *   Use for stable bundled icons (folder/tag/note).
 * @param staticIconKey see classification rules above.
 * @param captureSizePx pixel size of off-screen render surface. Should exceed
 *   the largest tier tile size — bigger gives the atlas headroom to downscale.
 * @param visibilityPaddingPx off-viewport buffer kept "visible".
 * @param visibilityDebounceMs debounce on pan/zoom before recomputing visibility.
 * @param content composable rendered off-screen per visible composable-typed
 *   node. Call `setReady(true)` once async loads inside it finish.
 */
@Composable
fun <Id, Data> rememberAtlasComposer(
    nodes: List<GraphNode<Id, Data>>,
    tiers: List<AtlasTier>,
    viewport: IntSize,
    userPosition: Offset,
    zoom: Float,
    coordinates: Map<Id, Offset>,
    staticIcons: ImmutableMap<String, Painter> = persistentMapOf(),
    staticIconKey: (Id, Data?) -> String? = { _, _ -> null },
    captureSizePx: Int = (tiers.maxOfOrNull { it.tileSizePx } ?: 64) * 2,
    visibilityPaddingPx: Float = 50f,
    visibilityDebounceMs: Long = 150L,
    content: @Composable CaptureScope.(GraphNode<Id, Data>) -> Unit,
): AtlasComposerHandle<Id, Data> {
    val density = LocalDensity.current
    val state = remember(density) { AtlasComposerState<Id, Data>(density) }

    // Push every recomposition. Guarded writes — mutableStateOf invalidates
    // observers on every write, even same-value writes, so we explicitly skip
    // no-op assignments to avoid spurious flow emissions.
    if (state.nodes !== nodes) state.nodes = nodes
    if (state.tiers !== tiers) state.tiers = tiers
    if (state.staticIcons !== staticIcons) state.staticIcons = staticIcons
    state.staticIconKey = staticIconKey
    state.captureSizePx = captureSizePx
    state.visibilityPaddingPx = visibilityPaddingPx
    state.contentLambda = content
    if (state.userPosition != userPosition) state.userPosition = userPosition
    if (state.zoom != zoom) state.zoom = zoom
    if (state.viewport != viewport) state.viewport = viewport
    // Reference compare is enough — upstream produces a fresh map per physics tick.
    if (state.coordinates !== coordinates) state.coordinates = coordinates

    // Visibility recompute loop.
    //
    // Reads `nodes` too so adding/removing nodes triggers a recompute even
    // without pan/zoom. First emission runs immediately (no debounce) so the
    // initial pass happens as soon as the inputs settle — without requiring
    // user interaction.
    LaunchedEffect(state, visibilityDebounceMs) {
        var firstEmission = true
        snapshotFlow {
            VisibilityInputs(
                state.userPosition, state.zoom, state.viewport,
                state.coordinates, state.nodes.size
            )
        }.collect {
            if (firstEmission) {
                firstEmission = false
            } else {
                kotlinx.coroutines.delay(visibilityDebounceMs)
            }
            state.recomputeVisibility()
        }
    }

    return remember(state) { AtlasComposerHandle(state) }
}

// =====================================================================
//  Internal state
// =====================================================================

@Stable
internal class AtlasComposerState<Id, Data>(private val density: Density) {

    // ----- inputs (snapshot-observable so visibility flow re-emits on change) -----
    var nodes by mutableStateOf<List<GraphNode<Id, Data>>>(emptyList())
    var tiers by mutableStateOf<List<AtlasTier>>(emptyList())
    var staticIcons by mutableStateOf<ImmutableMap<String, Painter>>(persistentMapOf())
    var staticIconKey: (Id, Data?) -> String? = { _, _ -> null }
    var captureSizePx: Int = 128
    var visibilityPaddingPx: Float = 50f
    var contentLambda: (@Composable CaptureScope.(GraphNode<Id, Data>) -> Unit)? = null

    // ----- camera (snapshot-observable) -----
    var userPosition by mutableStateOf(Offset.Zero)
    var zoom by mutableStateOf(1f)
    var viewport by mutableStateOf(IntSize.Zero)
    var coordinates by mutableStateOf<Map<Id, Offset>>(emptyMap())

    // ----- visibility -----
    private var visibleNodeIds by mutableStateOf<ImmutableList<Id>>(persistentListOf())

    // Composable-classified visible nodes, sorted nearest-first. This is the
    // pool that tier selections (TopByDistance / AllVisible) draw from, so
    // slots in a small tier like `hq` never get wasted on color-only or
    // static-icon nodes that don't need a composable capture in the first place.
    private var composableVisibleByDistance by mutableStateOf<ImmutableList<Id>>(persistentListOf())

    // ----- captured painters keyed by node id -----
    private val capturedPainters = mutableStateMapOf<Id, Painter>()
    private var captureVersion by mutableStateOf(0)

    /**
     * Classify a node's icon source. Centralizing this here keeps capture mounting,
     * tier inclusion, and key resolution in lockstep — they all must agree about
     * whether a node is color-only, static, or composable.
     */
    private fun classifyNode(node: GraphNode<Id, Data>): NodeIconKind {
        val key = staticIconKey(node.id, node.data) ?: return NodeIconKind.ColorOnly
        return if (staticIcons.containsKey(key)) NodeIconKind.Static(key)
        else NodeIconKind.Composable
    }

    fun recomputeVisibility() {
        val size = viewport
        if (size.width <= 0) return
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val pad = visibilityPaddingPx

        val scored = ArrayList<Pair<Id, Float>>(nodes.size.coerceAtMost(256))
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

        // Fallback: if NO node has coordinates yet (engine hasn't ticked), pick
        // up to `maxFallback` nodes in list order so captures can start eagerly.
        // This unblocks engines that delay coordinate emission until interaction.
        val ids: ImmutableList<Id> = if (scored.isEmpty() && nodes.isNotEmpty()) {
            val maxFallback = (tiers.maxOfOrNull {
                when (val sel = it.selection) {
                    TierSelection.All, TierSelection.AllVisible -> 60
                    is TierSelection.TopByDistance -> sel.count
                }
            } ?: 60).coerceAtLeast(1)
            nodes.asSequence().take(maxFallback).map { it.id }.toList().toImmutableList()
        } else {
            scored.map { it.first }.toImmutableList()
        }

        if (ids != visibleNodeIds) visibleNodeIds = ids

        // Build the composable-only nearest-first list. Cheap: classifyNode is
        // O(1) and `ids` is already distance-sorted.
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

        // Retain captures for currently-visible *or* All-tier nodes. If a tier
        // uses `All`, we still want its capture to survive panning that takes a
        // node off-screen — otherwise it would vanish from that tier's atlas.
        val needAllNodes = tiers.any { it.selection is TierSelection.All }
        val keep: HashSet<Id> = if (needAllNodes) {
            HashSet<Id>(nodes.size).also { set -> nodes.forEach { set.add(it.id) } }
        } else {
            ids.toHashSet()
        }
        if (capturedPainters.keys.retainAll(keep)) captureVersion++
    }

    @Composable
    fun MountCaptureHolders() {
        val contentFn = contentLambda ?: return
        val sizePx = captureSizePx

        // Capture mounts must cover BOTH visible nodes (for visible-only tiers)
        // and every node referenced by an `All` tier. Only `Composable` nodes
        // need a capture — color-only and static-icon nodes are handled
        // elsewhere (color = no atlas, static = staticIcons layer).
        val hasAllTier = tiers.any { it.selection is TierSelection.All }
        val mountIds: List<Id> = if (hasAllTier) {
            // Visible first (so they render sooner under the snapshot scheduler),
            // then the rest of the list de-duplicated.
            val seen = HashSet<Id>()
            buildList {
                for (id in visibleNodeIds) if (seen.add(id)) add(id)
                for (node in nodes) if (seen.add(node.id)) add(node.id)
            }
        } else {
            visibleNodeIds
        }

        Box(Modifier.size(0.dp)) {
            mountIds.forEach { id ->
                val node = nodes.firstOrNull { it.id == id } ?: return@forEach
                if (classifyNode(node) !is NodeIconKind.Composable) return@forEach
                key(id) {
                    CaptureHolder(
                        node = node,
                        sizePx = sizePx,
                        content = contentFn,
                        onCaptured = { painter ->
                            if (capturedPainters[id] !== painter) {
                                capturedPainters[id] = painter
                                captureVersion++
                            }
                        },
                    )
                }
            }
        }
    }

    // Per-tier atlas memoization. Keyed by (selected ids order, painter
    // identities, tileSize, isCircular). When `nodes` changes but the selected
    // set + painter references don't, this short-circuits the `createSvgAtlas`
    // call and returns the previously-built AtlasState — same `version`, same
    // bitmap reference, so downstream consumers see no change either.
    private val tierAtlasCache = HashMap<String, TierAtlasCacheEntry>()

    val atlasLayers: AtlasLayers by derivedStateOf {
        @Suppress("UNUSED_VARIABLE")
        val v = captureVersion
        val tierLayers = tiers.mapNotNull { buildTierAtlas(it) }
        val staticLayer = buildStaticAtlas()
        val all = buildList {
            addAll(tierLayers)
            staticLayer?.let { add(it) }
        }
        if (all.isEmpty()) AtlasLayers.EMPTY else AtlasLayers(all.toPersistentList())
    }

    private fun buildTierAtlas(tier: AtlasTier): AtlasState? {
        // Pick the candidate id list for this tier. For visible-based tiers we
        // draw from `composableVisibleByDistance` so slots are spent only on
        // nodes that need composable rendering — color/static nodes can't burn
        // an `hq` slot and starve a further-out composable node.
        val candidates: List<Id> = when (val sel = tier.selection) {
            TierSelection.All -> {
                // Filter the full list to composable nodes (color/static don't
                // belong in tier atlases at all).
                val out = ArrayList<Id>(nodes.size)
                for (node in nodes) {
                    if (classifyNode(node) is NodeIconKind.Composable) out += node.id
                }
                out
            }
            TierSelection.AllVisible -> composableVisibleByDistance
            is TierSelection.TopByDistance -> composableVisibleByDistance.take(sel.count)
        }
        if (candidates.isEmpty()) return null

        // Resolve to (id, painter) pairs. Skip ids whose painter hasn't been
        // captured yet — they'll appear once their CaptureHolder reports back.
        val selected = ArrayList<Id>(candidates.size)
        val painters = ArrayList<Painter>(candidates.size)
        for (id in candidates) {
            val p = capturedPainters[id] ?: continue
            selected += id
            painters += p
        }
        if (painters.isEmpty()) return null

        // Cache lookup. Identity hash of painter list + ordered ids fully
        // determines the resulting bitmap, so a hit means we can reuse it
        // bitmap-and-version-identical.
        val cached = tierAtlasCache[tier.name]
        if (cached != null
            && cached.tileSizePx == tier.tileSizePx
            && cached.isCircular == tier.isCircular
            && cached.ids == selected
            && cached.painterIdentities.size == painters.size
            && painters.indices.all { cached.painterIdentities[it] === painters[it] }
        ) {
            return cached.atlas
        }

        // Each tier registers its painters under the *same* per-node key. The
        // layer stack then resolves a node's key against tiers in declared
        // priority order — hq wins when present, otherwise hq1, otherwise lq.
        // This is what makes mipmap-style quality fallback work: a single key
        // walks the pyramid until it hits a layer that has it.
        val indexes = mutableMapOf<String, Int>()
        for ((i, id) in selected.withIndex()) indexes[nodeKey(id)] = i

        val result = createSvgAtlas(painters, density, tier.tileSizePx)
        val atlas = AtlasState(
            bitmap = result.imageBitmap,
            indexMap = indexes.toPersistentMap(),
            columns = result.columns,
            tileSizePx = result.tileSizePx,
            isCircular = tier.isCircular,
            version = Clock.System.now().toEpochMilliseconds(),
        )
        tierAtlasCache[tier.name] = TierAtlasCacheEntry(
            ids = selected.toList(),
            painterIdentities = painters.toList(),
            tileSizePx = tier.tileSizePx,
            isCircular = tier.isCircular,
            atlas = atlas,
        )
        return atlas
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
        val tileSize = tiers.maxOfOrNull { it.tileSizePx } ?: 64
        val result = createSvgAtlas(painters, density, tileSize)
        val atlas = AtlasState(
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

    /**
     * Three-way resolution:
     *  1. `staticIconKey` returns null → null (color-only; renderer falls back
     *     to the node's color).
     *  2. Key matches a registered static icon → return it (resolved through
     *     the static layer).
     *  3. Otherwise return the shared per-node key. All tiers register their
     *     painters under this same key, so `AtlasLayers.resolve` walks tiers
     *     in declared priority order (hq → hq1 → lq) and returns whichever
     *     tier currently carries this node.
     */
    fun resolveIconKey(nodeId: Id, data: Data?): String? {
        val userKey = staticIconKey(nodeId, data) ?: return null
        if (staticIcons.containsKey(userKey)) {
            return if (atlasLayers.resolve(userKey) != null) userKey else null
        }
        val key = nodeKey(nodeId)
        return if (atlasLayers.resolve(key) != null) key else null
    }

    private fun nodeKey(id: Id): String = "node:$id"
}

private data class TierAtlasCacheEntry(
    val ids: List<Any?>,
    val painterIdentities: List<Painter>,
    val tileSizePx: Int,
    val isCircular: Boolean,
    val atlas: AtlasState,
)

private sealed interface NodeIconKind {
    data object ColorOnly : NodeIconKind
    data class Static(val key: String) : NodeIconKind
    data object Composable : NodeIconKind
}

private data class VisibilityInputs(
    val offset: Offset,
    val zoom: Float,
    val viewport: IntSize,
    val coordinates: Map<*, Offset>,
    val nodeCount: Int,
)

// =====================================================================
//  CaptureScope impl + capture holder
// =====================================================================

@Stable
internal class CaptureScopeImpl : CaptureScope {
    private var readyA by mutableStateOf(false)
    override val isReadySignaled: Boolean get() = readyA
    override fun setReady(ready: Boolean) { this.readyA = ready }
}

@Composable
private fun <Id, Data> CaptureHolder(
    node: GraphNode<Id, Data>,
    sizePx: Int,
    content: @Composable CaptureScope.(GraphNode<Id, Data>) -> Unit,
    onCaptured: (Painter) -> Unit,
) {
    val density = LocalDensity.current
    val scope = remember(node.id) { CaptureScopeImpl() }

    // captureKey flips when scope flips to ready, triggering a re-record.
    val captureKey = "${node.id}:${if (scope.isReadySignaled) "ready" else "pending"}"

    val captured = rememberPainterFromComposable(
        modifier = Modifier.size(with(density) { sizePx.toDp() }),
        captureKey = captureKey,
    ) {
        Box(Modifier.size(with(density) { sizePx.toDp() })) {
            content(scope, node)
        }
    }

    // For synchronous content that never calls setReady: auto-mark ready after
    // the first frame is dispatched, so we still record something.
    LaunchedEffect(node.id) {
        yield() // let the content compose once
        if (!scope.isReadySignaled) scope.setReady(true)
    }

    LaunchedEffect(captured, scope.isReadySignaled) {
        if (scope.isReadySignaled && captured != null) onCaptured(captured)
    }
}