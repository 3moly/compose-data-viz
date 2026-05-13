package com.threemoly.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.graph.ui.Graph
import com.threemoly.sample.base.graph.GraphSettingsContent
import com.threemoly.sample.base.graph.GraphState
import com.threemoly.sample.base.graph.ObsidianGraphData
import com.threemoly.sample.base.graph.ObsidianGraphNode
import com.threemoly.sample.base.io
import com.threemoly.sample.base.uikit.ObsSlider
import com.threemoly.sample.base.uikit.SettingsPanel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlin.random.Random

private val random = Random(124)

@Composable
fun GraphSample(state: MutableState<GraphState>, nodeCountState: MutableState<Float>) {
    val s = state.value

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.darker(0.5f))
    ) {
        Graph(
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
        colorValue = Color.Black.darker(1f - nextSize / 100f).value
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