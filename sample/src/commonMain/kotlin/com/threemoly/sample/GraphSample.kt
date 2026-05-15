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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
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
import com.moly3.dataviz.graph.features.atlas.AtlasTier
import com.moly3.dataviz.graph.features.atlas.func.createAtlasFromUrlsSuspend
import com.moly3.dataviz.graph.features.atlas.func.createSvgAtlas
import com.moly3.dataviz.graph.ui.AtlasPainterLoader
import com.moly3.dataviz.graph.ui.Graph
import com.moly3.dataviz.graph.ui.TierSelection
import com.moly3.dataviz.graph.ui.rememberAtlasComposer
import com.moly3.dataviz.graph.ui.rememberMovementTracker
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

    val context = LocalPlatformContext.current
    val coilImageLoader = remember { ImageLoader(context) }
    val loader: AtlasPainterLoader<String, ObsidianGraphData> = { node ->
        val index = state.value.graphNodes.indexOfFirst { it.id == node.id }
        if (index < 0 || index >= 100) null
        else {
            val req = ImageRequest.Builder(context)
                .data("https://picsum.photos/id/$index/300/300")
                .size(128)
                .allowConversionToBitmap(true)
                .build()
            coilImageLoader.execute(req).image?.toBitmap()?.asImage()?.asPainter(context)
        }
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val movement = rememberMovementTracker(idleMillis = 1000)
    LaunchedEffect(s.velocities) { if (s.velocities.isNotEmpty()) movement.trigger() }
    LaunchedEffect(s.zoom) { movement.trigger() }
    LaunchedEffect(s.graphUserPosition) { movement.trigger() }
    val handle = rememberAtlasComposer(
        nodes = state.value.graphNodes,
        tiers = listOf(
            AtlasTier(
                name = "hq",
                tileSizePx = 256,
                selection = TierSelection.TopByDistance(3),
                isCircular = false,
                freezeOnMove = true,   // <-- was false
            ),
            AtlasTier(
                name = "lq",
                tileSizePx = 48,
                selection = TierSelection.All,
                isCircular = false,
                freezeOnMove = false,  // LQ can keep updating; it's cheap
            )
        ),

        viewport = viewport,
        userPosition = s.graphUserPosition,
        zoom = s.zoom,
        coordinates = s.coordinates,
        loader = loader,
        loaderKey = state.value.graphNodes.size, // or any token that should invalidate
        staticIcons = persistentMapOf(
            KEY_FOLDER to scale,
            KEY_SHARE to share,
            KEY_CAT to catPainter
        ),
        staticIconKey = { id, data -> /* return KEY_FOLDER / null / etc */ "" },
        isMoving = movement.isMoving
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.darker(0.5f))
            .onGloballyPositioned { viewport = it.size }
    ) {
        Graph(
            atlasLayers = handle.atlasLayers, getIconKey = handle::resolveIconKey,
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
            for (atlas in handle.atlasLayers.layers) {
                Image(
                    modifier = Modifier.padding(16.dp).size(100.dp),
                    bitmap = atlas.bitmap,
                    contentDescription = "layer 1"
                )
            }
//            shittyAtlas.value?.let {
//                Image(
//                    modifier = Modifier.padding(16.dp).size(100.dp),
//                    bitmap = it.bitmap,
//                    contentDescription = "layer 1"
//                )
//            }
//            Image(
//                modifier = Modifier.padding(16.dp).size(100.dp),
//                bitmap = primaryAtlas.bitmap,
//                contentDescription = "layer 0"
//            )
//            fallbackAtlas?.let {
//                Image(
//                    modifier = Modifier.padding(16.dp).size(100.dp),
//                    bitmap = it.bitmap,
//                    contentDescription = "layer 1"
//                )
//            }
//            Text(text = counter.value.toString())
        }
        Box(Modifier.size(100.dp).background(if (movement.isMoving) Color.Magenta else Color.Green))

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