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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowConversionToBitmap
import coil3.toBitmap
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.func.rememberPainterFromComposable
import com.moly3.dataviz.graph.features.atlas.AtlasLayers
import com.moly3.dataviz.graph.features.atlas.AtlasState
import com.moly3.dataviz.graph.features.atlas.func.createAtlasFromUrlsSuspend
import com.moly3.dataviz.graph.features.atlas.func.createSvgAtlas
import com.moly3.dataviz.graph.ui.Graph
import com.moly3.dataviz.sample.resources.Res
import com.moly3.dataviz.sample.resources.cat
import com.threemoly.sample.base.graph.GraphState
import com.threemoly.sample.base.graph.ObsidianGraphData
import com.threemoly.sample.base.graph.ObsidianGraphNode
import com.threemoly.sample.base.io
import com.threemoly.sample.base.uikit.SettingsPanel
import com.threemoly.sample.base.uikit.icons.Scale
import com.threemoly.sample.base.uikit.icons.Share
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random

private val random = Random(124)

// Stable keys for atlas tile lookup
const val KEY_FOLDER = "icon_folder"
const val KEY_SHARE = "icon_share"
private const val KEY_CAT = "icon_cat"

@Composable
fun GraphSample(state: MutableState<GraphState>, nodeCountState: MutableState<Float>) {
    val s = state.value
    val scale: Painter = rememberVectorPainter(Scale)
    val share: Painter = rememberVectorPainter(Share)
    val catPainter = painterResource(Res.drawable.cat)

    val density = LocalDensity.current
    val catty = rememberPainterFromComposable(modifier = Modifier.size(50.dp), captureKey = "") {
        Box(Modifier.fillMaxSize().background(Color.Magenta)) {
            Image(modifier = Modifier.padding(16.dp), painter = catPainter, contentDescription = "")
        }
    }
    val context = LocalPlatformContext.current
    val coilImageLoader = remember { ImageLoader(context) }
    val shittyAtlas = remember { mutableStateOf<AtlasState?>(null) }

    LaunchedEffect(state.value.graphNodes) {
        // 1. Create the mapped data
        val urlsPairs = state.value.graphNodes.mapIndexed { index, node ->
            Pair(index, node) to "https://picsum.photos/id/${index}/300/300"
        }

        val fallback = ColorPainter(Color.Transparent)

        val textureAtlas = createAtlasFromUrlsSuspend(
            urls = urlsPairs.map { it.second },
            density = density,
            tileSizePx = 128,
            concurrencyLimit = 15,
            fallbackPainter = fallback,
            imageLoader = { url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(128)
                    .allowConversionToBitmap(true) // CRITICAL: Hardware bitmaps can't always be drawn onto Canvas
                    .build()

                val result = coilImageLoader.execute(request)
                val drawable = result.image?.toBitmap()
                drawable?.asImage()?.asPainter(context)
            }
        )

        // 3. Update State (Now your indices will perfectly match!)
        shittyAtlas.value = AtlasState(
            bitmap = textureAtlas.imageBitmap,
            indexMap = urlsPairs
                .associate { (pair, _) -> pair.second.id to pair.first }
                .toPersistentMap(),
            columns = textureAtlas.columns,
            tileSizePx = textureAtlas.tileSizePx,
            isCircular = false
        )
    }

//    LaunchedEffect(state.value.graphNodes) {
//        val urlsPairs = state.value.graphNodes.mapIndexed { index, node ->
//            Pair(index,node) to "https://picsum.photos/id/${index}/300/300"
//        }
//        val textureAtlas = createAtlasFromUrlsSuspend(
//            urls = urlsPairs.map { d -> d.second },
//            density = density,
//            tileSizePx = 128,
//            imageLoader = { url ->
//                val request = ImageRequest.Builder(context)
//                    .data(url)
//                    .size(128)
//                    .build()
//                val result = coilImageLoader.execute(request)
//                val drawable = result.image?.toBitmap()
//                drawable?.asImage()?.asPainter(context)
//            }
//        )
//
//        shittyAtlas.value = AtlasState(
//            bitmap = textureAtlas.imageBitmap,
//            indexMap = urlsPairs
//                .associate { (number, _) -> number.second.id to number.first }
//                .toPersistentMap(),
//            columns = textureAtlas.columns,
//            tileSizePx = textureAtlas.tileSizePx,
//            isCircular = false
//        )
//    }
    // ---- Layer 0: primary atlas (bundled / always-present icons) ----
    val primaryAtlas = remember(catty, scale, share, density) {
        val textureAtlas = createSvgAtlas(
            painters = listOf(catty ?: scale, share),
            density = density,
            tileSizePx = 64
        )
        AtlasState(
            bitmap = textureAtlas.imageBitmap,
            // Map each painter's slot in `painters` list to a stable key
            indexMap = persistentMapOf(
                KEY_FOLDER to 0, // catty (or scale fallback) lives at index 0
                KEY_SHARE to 1,  // share lives at index 1
            ),
            columns = textureAtlas.columns,
            tileSizePx = textureAtlas.tileSizePx,
        )
    }

    // ---- Layer 1: fallback / dynamic atlas (example: a secondary atlas) ----
    // For now this is just a placeholder example — wire your real 2nd atlas here.
    // If you don't have one yet, just omit it from the layers list.
    val fallbackAtlas: AtlasState? = remember(catPainter, density) {
        val built = createSvgAtlas(
            painters = listOf(catPainter),
            density = density,
            tileSizePx = 64
        )
        AtlasState(
            bitmap = built.imageBitmap,
            indexMap = persistentMapOf(
                KEY_CAT to 0,
            ),
            columns = built.columns,
            tileSizePx = built.tileSizePx,
        )
    }

    // Compose the layers — order matters: layer 0 is checked first
    val atlasLayers = remember(shittyAtlas.value, primaryAtlas, fallbackAtlas) {
        val att = shittyAtlas.value
        AtlasLayers(
            layers = if (att != null) persistentListOf(att) else persistentListOf()
//            layers = if (fallbackAtlas != null && att != null) {
//                persistentListOf(att, primaryAtlas, fallbackAtlas)
//            } else {
//                persistentListOf(primaryAtlas)
//            }
        )
    }

    val counter = remember { mutableStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.darker(0.5f))
    ) {
        Graph(
            getNodeGroups = { _, data ->
                when (data) {
                    is ObsidianGraphData.Collection -> listOf("collection")
                    is ObsidianGraphData.CollectionRow -> listOf("row")
                    is ObsidianGraphData.File -> listOf("file")
                    is ObsidianGraphData.Tag -> listOf("tag")
                }
            },
            getGroupColor = { groupName ->
                when (groupName) {
                    "collection" -> Color.Black
                    "row" -> Color.Magenta
                    "file" -> Color.Blue
                    "tag" -> Color.Cyan
                    else -> Color.Red
                }
            },
            isImmediateReheatOnUpdate = true,
            customPopup = {
                val cp =
                    rememberAsyncImagePainter("https://composedataviz.3moly.com/images/cat4.jpg")
                Image(
                    painter = cp,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
            },

            atlasLayers = atlasLayers,
            getIconKey = { nodeId, data ->
                nodeId
//                "https://picsum.photos/id/1/300/300"
//                counter.value += 1
//                val node = s.graphNodes.find { it.id == nodeId }
//                when {
//                    node?.name?.contains("Folder") == true -> KEY_FOLDER  // resolves in layer 0
//                    node?.name?.contains("Cat") == true -> KEY_CAT     // resolves in layer 1 (fallback)
//                    node?.name?.contains("Share") == true -> KEY_SHARE   // resolves in layer 0
//                    else -> null  // no icon -> background circle is drawn
//                }
            },

            settings = s.graphSettings,
            consume = false,

            connections = s.connections,
            stateNodes = s.graphNodes,
            coordinates = s.coordinates,
            velocities = s.velocities,

            zoom = s.zoom,
            onZoomChange = { state.value = state.value.copy(zoom = it) },

            userPosition = s.graphUserPosition,
            onCentralGlobalPosition = {
                state.value = state.value.copy(
                    graphUserPosition = it
                )
            },

            onNodeClick = { node ->
                for (item in 0 until 50) {
                    state.spawnConnectedNode(node.id)
                }
            },

            onCoordinatesUpdate = {
                state.value = state.value.copy(coordinates = it.toPersistentMap())
            },
            io = io,
        )

        // Debug preview: show both atlas bitmaps side-by-side
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shittyAtlas.value?.let {
                Image(
                    modifier = Modifier.padding(16.dp).size(100.dp),
                    bitmap = it.bitmap,
                    contentDescription = "layer 1"
                )
            }
            Image(
                modifier = Modifier.padding(16.dp).size(100.dp),
                bitmap = primaryAtlas.bitmap,
                contentDescription = "layer 0"
            )
            fallbackAtlas?.let {
                Image(
                    modifier = Modifier.padding(16.dp).size(100.dp),
                    bitmap = it.bitmap,
                    contentDescription = "layer 1"
                )
            }
            Text(text = counter.value.toString())
        }

        // -------------- Settings panel --------------
        SettingsPanel(
            backgroundColor = Color.White,
            isShowSettings = s.isShowSettings,
            onSetSettings = {
                state.value = state.value.copy(isShowSettings = !state.value.isShowSettings)
            },
        ) {
            GraphSettingsContent(
                modifier = Modifier,
                settings = s.graphSettings,
                onChange = { state.value = state.value.copy(graphSettings = it) },
                zoom = s.zoom,
                nodeCount = s.graphNodes.size,
                onNodeCountChange = { nodeCountState.value = it.toFloat() }
            )
        }
    }
}

private fun MutableState<GraphState>.spawnConnectedNode(sourceId: String) {
    val current = value
    val newId = "node ${random.nextInt()}"
    val nextSize = current.graphNodes.size + 1

    val newNode = ObsidianGraphNode(
        newId,
        name = newId,
        data = ObsidianGraphData.File(""),
        colorValue = Color.Red.darker(1f - nextSize / 100f).value
    )

    val nodes = current.graphNodes.toMutableList().apply { add(newNode) }
    val connections = current.connections.toMutableMap().apply {
        put(newId, persistentListOf(sourceId))
        put(sourceId, ((this[sourceId] ?: listOf()) + newId).toPersistentList())
    }

    value = current.copy(
        graphNodes = nodes.toPersistentList(),
        connections = connections.toPersistentMap(),
    )
}