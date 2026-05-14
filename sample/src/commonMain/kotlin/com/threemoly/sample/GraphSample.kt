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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.func.rememberPainterFromComposable
import com.moly3.dataviz.graph.ui.AtlasLayers
import com.moly3.dataviz.graph.ui.AtlasState
import com.moly3.dataviz.graph.ui.Graph
import com.moly3.dataviz.graph.ui.createSvgAtlas
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
private const val KEY_FOLDER = "icon_folder"
private const val KEY_SHARE = "icon_share"
private const val KEY_CAT = "icon_cat"

@Composable
fun GraphSample(state: MutableState<GraphState>, nodeCountState: MutableState<Float>) {
    val s = state.value
    val scale: Painter = rememberVectorPainter(Scale)
    val share: Painter = rememberVectorPainter(Share)
    val catPainter = painterResource(Res.drawable.cat)

    val density = LocalDensity.current
    val catty = rememberPainterFromComposable(modifier = Modifier.size(50.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Magenta)) {
            Image(modifier = Modifier.padding(16.dp), painter = catPainter, contentDescription = "")
        }
    }

    // ---- Layer 0: primary atlas (bundled / always-present icons) ----
    val primaryAtlas = remember(catty, scale, share, density) {
        val built = createSvgAtlas(
            painters = listOf(catty ?: scale, share),
            density = density,
            tileSizePx = 64
        )
        AtlasState(
            bitmap = built.imageBitmap,
            // Map each painter's slot in `painters` list to a stable key
            indexMap = persistentMapOf(
                KEY_FOLDER to 0, // catty (or scale fallback) lives at index 0
                KEY_SHARE to 1,  // share lives at index 1
            ),
            columns = built.columns,
            tileSizePx = built.tileSizePx,
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
    val atlasLayers = remember(primaryAtlas, fallbackAtlas) {
        AtlasLayers(
            layers = if (fallbackAtlas != null) {
                persistentListOf(primaryAtlas, fallbackAtlas)
            } else {
                persistentListOf(primaryAtlas)
            }
        )
    }

    val counter = remember { mutableStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.darker(0.5f))
    ) {
        Graph(
            getNodeGroups = { _, _ -> listOf("") },
            simpleCanvas = false,
            isImmediateReheatOnUpdate = true,
            customPopup = {
                val cp = rememberAsyncImagePainter("https://composedataviz.3moly.com/images/cat4.jpg")
                Image(
                    painter = cp,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
            },

            atlasLayers = atlasLayers,
            getIconKey = { nodeId, data ->
                counter.value += 1
                val node = s.graphNodes.find { it.id == nodeId }
                when {
                    node?.name?.contains("Folder") == true -> KEY_FOLDER  // resolves in layer 0
                    node?.name?.contains("Cat") == true    -> KEY_CAT     // resolves in layer 1 (fallback)
                    node?.name?.contains("Share") == true  -> KEY_SHARE   // resolves in layer 0
                    else -> null  // no icon -> background circle is drawn
                }
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

        // Debug preview: show both atlas bitmaps side-by-side
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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