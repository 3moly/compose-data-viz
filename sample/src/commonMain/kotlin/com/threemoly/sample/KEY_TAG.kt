package com.threemoly.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.engine.impl.ultra.UltraFastEngine
import com.moly3.dataviz.core.graph.model.*
import com.moly3.dataviz.func.rememberPainterFromComposable
import com.moly3.dataviz.graph.ui.AtlasLayers
import com.moly3.dataviz.graph.ui.AtlasState
import com.moly3.dataviz.graph.ui.Graph
import com.moly3.dataviz.graph.ui.createSvgAtlas
import com.threemoly.sample.base.io
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.debounce
import kotlin.time.Clock

// --- 1. Pure Data Model ---
enum class SimpleNodeType { NOTE, IMAGE, TAG, FOLDER }

data class SimpleNodeData(
    val type: SimpleNodeType,
    val imageUrl: String? = null
)

// --- 2. Constants & Helpers ---
private const val KEY_TAG = "tag"
private const val KEY_NOTE = "note"
private const val KEY_FOLDER = "folder"
private const val KEY_IMAGE_GENERIC = "image"
private fun imageKey(url: String) = "image_$url"

private const val LAYER0_SVG_TILE_PX = 64
private const val LAYER1_HQ_MAX_IMAGES = 12
private const val MAX_VISIBLE_IMAGES = 60

private fun isNodeVisible(
    nodeX: Float, nodeY: Float, offset: Offset, zoom: Float, w: Float, h: Float, pad: Float
): Boolean {
    val screenX = (nodeX + offset.x) * zoom + (w / 2f)
    val screenY = (nodeY + offset.y) * zoom + (h / 2f)
    return screenX >= -pad && screenX <= w + pad && screenY >= -pad && screenY <= h + pad
}

/**
 * Holds a snapshot-observable registry of captured painters keyed by URL.
 *
 * Single source of truth: a URL is "ready" iff it appears in [painters].
 * [version] bumps on every successful capture so atlas-building `remember`
 * blocks invalidate reliably (hashing a set of painters is unreliable — same
 * URLs with different bitmaps would collide).
 */
@Stable
private class PainterRegistry {
    private val _painters = mutableStateMapOf<String, Painter>()
    val painters: Map<String, Painter> get() = _painters
    var version by mutableStateOf(0)
        private set

    fun put(url: String, painter: Painter) {
        _painters[url] = painter
        version++
    }

    fun retainOnly(urls: Set<String>) {
        val removed = _painters.keys.retainAll(urls)
        if (removed) version++
    }

    fun isReady(url: String): Boolean = _painters.containsKey(url)
}

@Composable
fun StandalonePicsumGraphSample() {
    val testNodes = (0 until 200).map { i ->
        val isImage = i % 3 == 0
        GraphNode(
            id = i.toString(),
            name = "Node $i",
            data = SimpleNodeData(
                type = if (isImage) SimpleNodeType.IMAGE else SimpleNodeType.NOTE,
                imageUrl = if (isImage) "https://static.vecteezy.com/system/resources/thumbnails/009/273/280/small/concept-of-loneliness-and-disappointment-in-love-sad-man-sitting-element-of-the-picture-is-decorated-by-nasa-free-photo.jpg" else null
            )
        )
    }
    val engine = remember { UltraFastEngine<String, SimpleNodeData>() }
    StandalonePicsumGraph(
        engine = engine,
        nodes = testNodes,
        connections = mutableMapOf(),
        initialCoordinates = mapOf()
    )
}

// --- 3. Main Standalone Composable ---
@Composable
fun StandalonePicsumGraph(
    engine: IGraphEngine<String, SimpleNodeData>,
    nodes: List<GraphNode<String, SimpleNodeData>>,
    connections: Map<String, List<String>>,
    initialCoordinates: Map<String, Offset>
) {
    var zoom by remember { mutableStateOf(1f) }
    var userPosition by remember { mutableStateOf(Offset.Zero) }
    var coordinates by remember { mutableStateOf(initialCoordinates) }
    var velocities by remember { mutableStateOf(emptyMap<String, Offset>()) }
    var viewPort by remember { mutableStateOf(IntSize.Zero) }

    val settings = remember {
        GraphSettings.Default.copy(
            view = GraphViewSettings.Default.copy(targetFrameMs = 16L, circleSize = 5f),
            text = GraphTextSettings.Default.copy(normalFontSize = 3.sp),
            theme = GraphTheme.Default.copy(textColor = Color.White),
            textStyle = TextStyle.Default,
            zoom = GraphZoomSettings.Default.copy(maxZoom = 50f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .onGloballyPositioned { viewPort = it.size }
    ) {
        val density = LocalDensity.current
        var visibleImageUrls by remember { mutableStateOf<ImmutableList<String>>(persistentListOf()) }

        val sizerPxInt = with(density) { 128.dp.toPx() }.toInt()
        val lqTilePxInt = (sizerPxInt / 2).coerceAtLeast(16)

        // Single registry — only ever contains URLs that have actually captured.
        val registry = remember { PainterRegistry() }

        // Viewport tracking → which images deserve a capture holder right now.
        LaunchedEffect(Unit) {
            snapshotFlow { VisibilityInputs(userPosition, zoom, viewPort, coordinates) }
                .debounce(150L)
                .collect { (offset, currentZoom, size, coords) ->
                    if (size.width <= 0) return@collect

                    val visible = nodes.asSequence()
                        .filter { it.data?.type == SimpleNodeType.IMAGE && it.data.imageUrl != null }
                        .mapNotNull { node ->
                            val coord = coords[node.id] ?: return@mapNotNull null
                            if (!isNodeVisible(
                                    coord.x, coord.y, offset, currentZoom,
                                    size.width.toFloat(), size.height.toFloat(), 50f
                                )
                            ) return@mapNotNull null
                            val screenDx = (coord.x + offset.x) * currentZoom
                            val screenDy = (coord.y + offset.y) * currentZoom
                            node to (screenDx * screenDx + screenDy * screenDy)
                        }
                        .sortedBy { it.second }
                        .take(MAX_VISIBLE_IMAGES)
                        .map { it.first.data!!.imageUrl!! }
                        .toList()
                        .toImmutableList()

                    if (visible != visibleImageUrls) visibleImageUrls = visible
                }
        }

        // Mount one off-screen capture holder per visible URL.
        // Coil paints into rememberPainterFromComposable, which records to a Painter.
        // On successful capture we push into the registry (which bumps version).
        Box(Modifier.size(0.dp)) {
            visibleImageUrls.forEach { url ->
                key(url) {
                    CoilCaptureHolder(
                        url = url,
                        sizePx = sizerPxInt,
                        onCaptured = { painter -> registry.put(url, painter) }
                    )
                }
            }
        }

        // Drop captures for URLs that scrolled out of view.
        LaunchedEffect(visibleImageUrls) {
            registry.retainOnly(visibleImageUrls.toSet())
        }

        val imgIcon = rememberVectorPainter(Icons.Default.Image)
        val tagIcon = rememberVectorPainter(Icons.Default.Tag)
        val noteIcon = rememberVectorPainter(Icons.Default.Note)
        val folderIcon = rememberVectorPainter(Icons.Default.Folder)

        // L0: vector icons. Stable across the session — built once.
        val layer0Svg: AtlasState? = remember(density) {
            buildSvgLayer(density, LAYER0_SVG_TILE_PX, tagIcon, noteIcon, folderIcon, imgIcon)
        }

        // Snapshot-observable read of (visible URLs ∩ ready URLs) in stable order.
        // This is THE thing that drives atlas rebuilds. By reading `registry.version`
        // inside derivedStateOf we guarantee invalidation when a new image lands.
        val readyVisibleUrls: ImmutableList<String> by remember {
            derivedStateOf {
                @Suppress("UNUSED_VARIABLE")
                val v = registry.version // observe
                visibleImageUrls.filter { registry.isReady(it) }.toImmutableList()
            }
        }

        val hqUrls: ImmutableList<String> by remember {
            derivedStateOf { readyVisibleUrls.take(LAYER1_HQ_MAX_IMAGES).toImmutableList() }
        }

        // L1: HQ atlas of ready, close-to-center images.
        val layer1Hq: AtlasState? = remember(hqUrls, sizerPxInt, density) {
            buildImageLayer(hqUrls, registry.painters, density, sizerPxInt)
        }

        // L2: LQ atlas of all ready visible images.
        val layer2Lq: AtlasState? = remember(readyVisibleUrls, lqTilePxInt, density) {
            buildImageLayer(readyVisibleUrls, registry.painters, density, lqTilePxInt)
        }

        val atlasLayers: AtlasLayers = remember(layer0Svg, layer1Hq, layer2Lq) {
            val layers = buildList {
                layer0Svg?.let { add(it) }
                layer1Hq?.let { add(it) }
                layer2Lq?.let { add(it) }
            }
            if (layers.isEmpty()) AtlasLayers.EMPTY else AtlasLayers(layers.toPersistentList())
        }

        val atlasLayersUpdated by rememberUpdatedState(atlasLayers)

        Graph(
            modifier = Modifier.fillMaxSize().background(Color.White),
            engine = engine,
            simpleCanvas = false,
            settings = settings,
            atlasLayers = atlasLayers,
            connections = connections,
            stateNodes = nodes,
            getIconKey = { _, data ->
                when (data?.type) {
                    SimpleNodeType.FOLDER -> KEY_FOLDER
                    SimpleNodeType.TAG -> KEY_TAG
                    SimpleNodeType.IMAGE -> {
                        val pathKey = data.imageUrl?.let { imageKey(it) } ?: KEY_IMAGE_GENERIC
                        // Fall back to generic image icon until this URL lands in an atlas.
                        if (atlasLayersUpdated.resolve(pathKey) != null) pathKey else KEY_IMAGE_GENERIC
                    }
                    else -> KEY_NOTE
                }
            },
            coordinates = coordinates,
            velocities = velocities,
            zoom = zoom,
            onZoomChange = { zoom = it },
            userPosition = userPosition,
            onCentralGlobalPosition = { userPosition = it },
            onNodeClick = { },
            onCoordinatesUpdate = { coordinates = it },
            onVelocitiesUpdate = { velocities = it },
            consume = false,
            io = io
        )

        // Debug overlay
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                layer0Svg?.bitmap?.let {
                    Text("L0 SVG (${it.width}x${it.height})")
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(80.dp))
                }
                layer1Hq?.bitmap?.let {
                    Text("L1 HQ (${hqUrls.size}/${readyVisibleUrls.size} ready, tile $sizerPxInt)")
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(100.dp))
                }
                layer2Lq?.bitmap?.let {
                    Text("L2 LQ (${readyVisibleUrls.size}/${visibleImageUrls.size} ready, tile $lqTilePxInt)")
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(100.dp))
                }
            }
        }
    }
}

/** Combined input bundle for the visibility snapshotFlow — equals semantics for free via data class. */
private data class VisibilityInputs(
    val offset: Offset,
    val zoom: Float,
    val viewport: IntSize,
    val coordinates: Map<String, Offset>
)

// --- 4. Atlas Builders ---

/**
 * Builds an atlas containing ONLY ready painters. No transparent placeholders —
 * if a URL isn't ready, it's simply absent from the atlas and the engine's
 * getIconKey fallback handles the miss.
 */
private fun buildImageLayer(
    urls: ImmutableList<String>,
    painters: Map<String, Painter>,
    density: Density,
    tileSizePx: Int
): AtlasState? {
    if (urls.isEmpty()) return null

    val indexes = mutableMapOf<String, Int>()
    val resolvedPainters = mutableListOf<Painter>()

    for (url in urls) {
        val p = painters[url] ?: continue // skip not-ready; never atlas-place a placeholder
        indexes[imageKey(url)] = resolvedPainters.size
        resolvedPainters += p
    }
    if (resolvedPainters.isEmpty()) return null

    val result = createSvgAtlas(resolvedPainters, density, tileSizePx)
    return AtlasState(
        bitmap = result.imageBitmap,
        indexMap = indexes.toPersistentMap(),
        columns = result.columns,
        tileSizePx = result.tileSizePx,
        isCircular = false,
        version = Clock.System.now().toEpochMilliseconds()
    )
}

private fun buildSvgLayer(
    density: Density,
    tileSizePx: Int,
    tag: Painter,
    note: Painter,
    folder: Painter,
    img: Painter
): AtlasState {
    val indexes = mutableMapOf<String, Int>()
    val painters = mutableListOf<Painter>()
    var idx = 0

    painters += tag;    indexes[KEY_TAG] = idx++
    painters += note;   indexes[KEY_NOTE] = idx++
    painters += folder; indexes[KEY_FOLDER] = idx++
    painters += img;    indexes[KEY_IMAGE_GENERIC] = idx++

    val result = createSvgAtlas(painters, density, tileSizePx)
    return AtlasState(
        bitmap = result.imageBitmap,
        indexMap = indexes.toPersistentMap(),
        columns = result.columns,
        tileSizePx = result.tileSizePx,
        isCircular = true,
        version = Clock.System.now().toEpochMilliseconds()
    )
}

/**
 * Off-screen Coil host that captures the loaded image into a Painter as soon
 * as Coil reports Success. We re-capture if the success state re-flips (e.g.
 * Coil swaps in a higher-res version), keyed on the painter identity.
 */
@Composable
private fun CoilCaptureHolder(
    url: String,
    sizePx: Int,
    onCaptured: (Painter) -> Unit,
) {
    val context = LocalPlatformContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .size(coil3.size.Size.ORIGINAL)
            .crossfade(false) // avoid capturing mid-fade frames
            .build()
    )
    val state by painter.state.collectAsState()
    val isReady = state is AsyncImagePainter.State.Success

    val captured = rememberPainterFromComposable(
        modifier = Modifier.size(with(LocalDensity.current) { sizePx.toDp() }),
        captureKey = isReady
    ) {
        Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize())
    }

    // Push to registry exactly once per (url, captured-instance) transition to Success.
    LaunchedEffect(captured, isReady) {
        if (isReady && captured != null) onCaptured(captured)
    }
}

private val TransparentPainter = object : Painter() {
    override val intrinsicSize get() = Size.Unspecified
    override fun DrawScope.onDraw() {}
}