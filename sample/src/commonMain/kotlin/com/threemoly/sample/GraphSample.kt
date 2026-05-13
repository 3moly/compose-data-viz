package com.threemoly.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Colors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.graph.ui.Graph
import com.moly3.dataviz.graph.ui.createSvgAtlas
import com.moly3.dataviz.sample.resources.Res
import com.moly3.dataviz.sample.resources.cat
import com.threemoly.sample.base.graph.GraphSettingsContent
import com.threemoly.sample.base.graph.GraphState
import com.threemoly.sample.base.graph.ObsidianGraphData
import com.threemoly.sample.base.graph.ObsidianGraphNode
import com.threemoly.sample.base.io
import com.threemoly.sample.base.uikit.ObsSlider
import com.threemoly.sample.base.uikit.SettingsPanel
import com.threemoly.sample.base.uikit.icons.Scale
import com.threemoly.sample.base.uikit.icons.Share
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random

private val random = Random(124)


@Composable
fun GraphSample(state: MutableState<GraphState>, nodeCountState: MutableState<Float>) {
    val s = state.value

//    val catPainter = rememberAsyncImagePainter("https://composedataviz.3moly.com/images/cat4.jpg")
    val scale: Painter = rememberVectorPainter(Scale)
    val share: Painter = rememberVectorPainter(Share)
    val catPainter = painterResource(Res.drawable.cat)
//    val context = LocalContext.current
//    val imageLoader = Coil.imageLoader(context)
//    val request = ImageRequest.Builder(context)
//        .data("https://composedataviz.3moly.com/images/cat4.jpg")
//        .allowHardware(false) // CRITICAL: You cannot draw Hardware Bitmaps to a Canvas
//        .build()
//    com.moly3.dataviz.sample.resources

    val density = LocalDensity.current
    val catty = rememberPainterFromComposable(modifier=Modifier.size(50.dp)){
        Box(Modifier.fillMaxSize().background(Color.Magenta)){
            Image(modifier = Modifier.padding(16.dp), painter = catPainter, contentDescription = "")
        }
    }
    val atlas = remember(catty, scale, share, density) {

        createSvgAtlas(
            painters = listOf(catty ?: scale, share),
            density = density,
            tileSizePx = 1024
        )
    }


    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.darker(0.5f))
    ) {
        Graph(
            atlas = atlas,
            settings = s.graphSettings,
            consume = false,
            getIconIndex = { nodeId ->
                val node = s.graphNodes.find { it.id == nodeId }
                when {
                    node?.name?.contains("Folder") == true -> 0 // Index of folder icon in painters list
                    node?.name?.contains("Node") == true -> 1  // Index of image icon
                    else -> 0 // No icon
                }
            },
            connections = s.connections,
            stateNodes = s.graphNodes,
            coordinates = s.coordinates,
            velocities = s.velocities,

            zoom = s.zoom,
            onZoomChange = { state.value = state.value.copy(zoom = it) },

            userPosition = s.graphUserPosition,
            onCentralGlobalPosition = {
                state.value = state.value.copy(
                    graphUserPosition = state.value.graphUserPosition + it
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
            onVelocitiesUpdate = {
                state.value = state.value.copy(velocities = it.toPersistentMap())
            },
            io = io,
        )
        atlas?.let {
            Image(
                modifier = Modifier.padding(16.dp).size(100.dp),
                bitmap = it.imageBitmap,
                contentDescription = ""
            )
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
            )
        }

        // -------------- Node count slider (bottom) --------------
        ObsSlider(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            value = nodeCountState.value,
            onValueChange = { nodeCountState.value = it },
            valueRange = 1f..50_000f,
        )
    }
}

// =====================================================================================
// Helpers
// =====================================================================================

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