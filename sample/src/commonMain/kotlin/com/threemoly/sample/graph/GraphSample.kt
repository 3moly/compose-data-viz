package com.threemoly.sample.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.graph.ui.Graph
import com.threemoly.sample.io
import com.threemoly.sample.uikit.ObsSlider
import com.threemoly.sample.uikit.ObsText
import com.threemoly.sample.uikit.SettingsPanel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.launch
import kotlin.random.Random

val random = Random(124)

fun randomColor(): Color {
    val colors = listOf(
        Color.Red,
        Color.Blue,
        Color.Green,
        Color.Yellow,
        Color.Magenta,
        Color.Cyan,
        Color.Gray,
        Color.DarkGray,
        Color(0xFF9C27B0), // Purple
        Color(0xFFFFC107)  // Amber
    )
    return colors.random()
}

fun generateRandomGraphState(
    nodeCount: Int,
    connectionsPercentPerNode: Float
): GraphState {

    val nodes = (1..nodeCount).map { index ->
        ObsidianGraphNode(
            id = "node_$index",
            name = "Node $index",
            data = ObsidianGraphData.File(""),
            colorValue = randomColor().value
        )
    }

    val nodeIds = nodes.map { it.id }
    val connections = mutableMapOf<String, PersistentList<String>>()

    nodeIds.forEach { nodeId ->
        val maxConnections = (nodeCount - 1) // max possible connections (excluding self)
        val targetConnectionCount = (maxConnections * connectionsPercentPerNode / 100f).toInt()

        // Randomly select target nodes to connect to
        val possibleTargets = nodeIds.filter { it != nodeId }.shuffled()
        val connectedNodes = possibleTargets.take(targetConnectionCount)

        connections[nodeId] = persistentListOf(*connectedNodes.toTypedArray())
    }

    return GraphState(
        graphNodes = persistentListOf(*nodes.toTypedArray()),
        connections = persistentMapOf(*makeBidirectional(connections).toList().toTypedArray())
    )
}

private fun makeBidirectional(connections: Map<String, List<String>>): Map<String, ImmutableList<String>> {
    val mutableGraph = connections.mapValues { it.value.toMutableList() }.toMutableMap()

    for ((node, connections) in connections) {
        for (target in connections) {
            mutableGraph.getOrPut(target) { mutableListOf() }.add(node)
        }
    }

    return mutableGraph.map { x -> x.key to x.value.toPersistentList() }.toMap()
}

@Composable
fun GraphSample(state: MutableState<GraphState>, nodeCountState: MutableState<Float>) {


    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.darker(0.5f))
        //todo .background(LocalAppTheme.current.colors.backgroundPrimary)
    ) {
        Graph(
            connections = state.value.connections,
            stateNodes = state.value.graphNodes,
            viewSettings = state.value.graphViewSettings,
            coordinates = state.value.coordinates,
            velocities = state.value.velocities,
            zoom = state.value.zoom,
            onZoomChange = {
                state.value = state.value.copy(zoom = it)
            },
            userPosition = state.value.graphUserPosition,
            onCentralGlobalPosition = {
                state.value = state.value.copy(graphUserPosition = state.value.graphUserPosition + it)
            },
            onNodeClick = { node ->
                val graphNodes = state.value.graphNodes.toMutableList()
                val connections = state.value.connections.toMutableMap()
                val newId = "node ${random.nextInt()}"
                graphNodes.add(
                    ObsidianGraphNode(
                        newId,
                        name = newId,
                        data = ObsidianGraphData.File(""),
                        colorValue = Color.Black.darker(1f - graphNodes.size / 100f).value
                    )
                )
                connections[newId] = persistentListOf(node.id)
                connections[node.id] =
                    ((connections[node.id] ?: listOf()) + newId).toPersistentList()
                state.value = state.value.copy(
                    graphNodes = graphNodes.toPersistentList(),
                    connections = connections.toPersistentMap()
                )
            },
            onCoordinatesUpdate = {

                state.value = state.value.copy(coordinates = it.toPersistentMap())
            },
            onVelocitiesUpdate = {
                state.value = state.value.copy(velocities = it.toPersistentMap())
            },
            primaryColor = Color.Red,
            fontColor = Color.White,
            io = io,
            circleLineColor = Color.Black,
            circleColor = Color.Yellow
        )
        SettingsPanel(
            backgroundColor = Color.White,
            isShowSettings = state.value.isShowSettings,
            onSetSettings = {
                state.value = state.value.copy(isShowSettings = !state.value.isShowSettings)
            }) {
            ObsText("zoom: ${state.value.zoom}")
            ObsText(
                "center force: ${state.value.graphViewSettings.centerForce}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.centerForce,
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                centerForce = it
                            )
                        )
                },
                valueRange = 0.001f..1f
            )
            ObsText(
                "link force: ${state.value.graphViewSettings.linkForce}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.linkForce,
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                linkForce = it
                            )
                        )
                },
                valueRange = 0.00001f..10f
            )
            ObsText(
                "link distance: ${state.value.graphViewSettings.linkDistance}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.linkDistance,
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                linkDistance = it
                            )
                        )
                },
                valueRange = 1f..500f
            )
            ObsText(
                "circle size: ${state.value.graphViewSettings.circleSize}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.circleSize,
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                circleSize = it
                            )
                        )
                },
                valueRange = 0.1f..50f
            )
            ObsText(
                "repel force: ${state.value.graphViewSettings.repelForce}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.repelForce,
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                repelForce = it
                            )
                        )
                },
                valueRange = 0.1f..100000f
            )
            ObsText(
                "unconnectedRepulsionMultiplier: ${state.value.graphViewSettings.unconnectedRepulsionMultiplier}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.unconnectedRepulsionMultiplier,
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                unconnectedRepulsionMultiplier = it
                            )
                        )
                },
                valueRange = 1f..10f
            )
            ObsText(
                "minMutualConnectionsForClustering: ${state.value.graphViewSettings.minMutualConnectionsForClustering}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.minMutualConnectionsForClustering.toFloat(),
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                minMutualConnectionsForClustering = it.toInt()
                            )
                        )
                },
                valueRange = 1f..10f
            )
            ObsText(
                "clusteringForce: ${state.value.graphViewSettings.clusteringForce}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.clusteringForce,
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                clusteringForce = it
                            )
                        )
                },
                valueRange = 1f..50f
            )
            ObsText(
                "longDistanceLinkMultiplier: ${state.value.graphViewSettings.longDistanceLinkMultiplier}"
            )
            ObsSlider(
                modifier = Modifier.width(150.dp),
                value = state.value.graphViewSettings.longDistanceLinkMultiplier,
                onValueChange = {
                    state.value =
                        state.value.copy(
                            graphViewSettings = state.value.graphViewSettings.copy(
                                longDistanceLinkMultiplier = it
                            )
                        )
                },
                valueRange = 1f..1000f
            )
        }
        ObsSlider(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 8.dp),
            value = nodeCountState.value,
            onValueChange = {
                nodeCountState.value = it
            },
            valueRange = 1f..200f
        )
    }
}