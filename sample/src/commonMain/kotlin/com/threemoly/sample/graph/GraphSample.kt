package com.threemoly.sample.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.graph.ui.Graph
import com.threemoly.sample.uikit.BIcon
import com.threemoly.sample.uikit.ObsSlider
import com.threemoly.sample.uikit.ObsText
import com.threemoly.sample.uikit.SettingsFuture
import com.threemoly.sample.uikit.SwitchOption
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random

val random = Random(124)

@Composable
fun GraphSample() {
    var state by remember {
        mutableStateOf(
            State(
                graphNodes = persistentListOf(
                    ObsidianGraphNode(
                        "oleg1",
                        name = "oleg",
                        data = ObsidianGraphData.File(""),
                        colorValue = Color.Black.value
                    ),
                    ObsidianGraphNode(
                        "oleg2",
                        name = "oleg 2",
                        data = ObsidianGraphData.File(""),
                        colorValue = Color.Black.value
                    )
                ),
                connections = persistentMapOf(
                    "oleg1" to persistentListOf("oleg2"),
                    "oleg2" to persistentListOf("oleg1"),
                )
            )
        )
    }
    val settingsWidth by animateDpAsState(if (state.isShowSettings) 130.dp else 48.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.darker(0.5f))
        //todo .background(LocalAppTheme.current.colors.backgroundPrimary)
    ) {
        Graph(
            connections = state.connections,
            stateNodes = state.graphNodes,
            viewSettings = state.graphViewSettings,
            coordinates = state.coordinates,
            velocities = state.velocities,
            zoom = state.zoom,
            onZoomChange = {
                state = state.copy(zoom = it)
            },
            userPosition = state.graphUserPosition,
            onCentralGlobalPosition = {
                state = state.copy(graphUserPosition = state.graphUserPosition + it)
            },
            onNodeClick = { node ->
                val graphNodes = state.graphNodes.toMutableList()
                val connections = state.connections.toMutableMap()
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
                connections[node.id] = ((connections[node.id] ?: listOf()) + newId).toPersistentList()
                state = state.copy(
                    graphNodes = graphNodes.toPersistentList(),
                    connections = connections.toPersistentMap()
                )
            },
            onCoordinatesUpdate = {

                state = state.copy(coordinates = it.toPersistentMap())
            },
            onVelocitiesUpdate = {
                state = state.copy(velocities = it.toPersistentMap())
            },
            primaryColor = Color.Red,
            fontColor = Color.White,
            io = Dispatchers.Main,
            circleLineColor = Color.Black,
            circleColor = Color.Yellow
        )

        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(8.dp))
                //todo .background(LocalAppTheme.current.colors.backgroundSecondary)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.width(settingsWidth).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                BIcon(imageVector = SettingsFuture, onClick = {
                    state = state.copy(isShowSettings = !state.isShowSettings)
                })
                AnimatedVisibility(state.isShowSettings) {
                    Column {
                        SwitchOption(
                            modifier = Modifier,
                            text = "Collections",
                            value = state.config.isCollections,
                            onClick = {
                                state =
                                    state.copy(config = state.config.copy(isCollections = !state.config.isCollections))
                            }
                        )
                        SwitchOption(
                            modifier = Modifier,
                            text = "Rows",
                            value = state.config.isRows,
                            onClick = {
                                state =
                                    state.copy(config = state.config.copy(isRows = !state.config.isRows))
                            }
                        )
                        SwitchOption(
                            modifier = Modifier,
                            text = "Tags",
                            value = state.config.isTags,
                            onClick = {
                                state =
                                    state.copy(config = state.config.copy(isTags = !state.config.isTags))
                            }
                        )
                        SwitchOption(
                            modifier = Modifier,
                            text = "Directories",
                            value = state.config.isShowDirectories,
                            onClick = {
                                state =
                                    state.copy(config = state.config.copy(isShowDirectories = !state.config.isShowDirectories))
                            }
                        )
                        SwitchOption(
                            modifier = Modifier,
                            text = "Orphans",
                            value = state.config.isOrphans,
                            onClick = {
                                state =
                                    state.copy(config = state.config.copy(isOrphans = !state.config.isOrphans))
                            }
                        )
                        SwitchOption(
                            modifier = Modifier,
                            text = "Real files only",
                            value = state.config.isRealFiles,
                            onClick = {
                                state =
                                    state.copy(config = state.config.copy(isRealFiles = !state.config.isRealFiles))
                            }
                        )
                        SwitchOption(
                            modifier = Modifier,
                            text = "Gradation",
                            value = state.config.isGradations,
                            onClick = {
                                state =
                                    state.copy(config = state.config.copy(isGradations = !state.config.isGradations))
                            }
                        )
                        ObsText("zoom: ${state.zoom}")
                        ObsText(
                            "center force: ${state.graphViewSettings.centerForce}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.centerForce,
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            centerForce = it
                                        )
                                    )
                            },
                            valueRange = 0.001f..1f
                        )
                        ObsText(
                            "link force: ${state.graphViewSettings.linkForce}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.linkForce,
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            linkForce = it
                                        )
                                    )
                            },
                            valueRange = 0.00001f..10f
                        )
                        ObsText(
                            "link distance: ${state.graphViewSettings.linkDistance}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.linkDistance,
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            linkDistance = it
                                        )
                                    )
                            },
                            valueRange = 1f..500f
                        )
                        ObsText(
                            "circle size: ${state.graphViewSettings.circleSize}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.circleSize,
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            circleSize = it
                                        )
                                    )
                            },
                            valueRange = 0.1f..50f
                        )
                        ObsText(
                            "repel force: ${state.graphViewSettings.repelForce}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.repelForce,
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            repelForce = it
                                        )
                                    )
                            },
                            valueRange = 0.1f..100000f
                        )
                        ObsText(
                            "unconnectedRepulsionMultiplier: ${state.graphViewSettings.unconnectedRepulsionMultiplier}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.unconnectedRepulsionMultiplier,
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            unconnectedRepulsionMultiplier = it
                                        )
                                    )
                            },
                            valueRange = 1f..10f
                        )
                        ObsText(
                            "minMutualConnectionsForClustering: ${state.graphViewSettings.minMutualConnectionsForClustering}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.minMutualConnectionsForClustering.toFloat(),
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            minMutualConnectionsForClustering = it.toInt()
                                        )
                                    )
                            },
                            valueRange = 1f..10f
                        )
                        ObsText(
                            "clusteringForce: ${state.graphViewSettings.clusteringForce}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.clusteringForce,
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            clusteringForce = it
                                        )
                                    )
                            },
                            valueRange = 1f..50f
                        )
                        ObsText(
                            "longDistanceLinkMultiplier: ${state.graphViewSettings.longDistanceLinkMultiplier}"
                        )
                        ObsSlider(
                            modifier = Modifier.width(150.dp),
                            value = state.graphViewSettings.longDistanceLinkMultiplier,
                            onValueChange = {
                                state =
                                    state.copy(
                                        graphViewSettings = state.graphViewSettings.copy(
                                            longDistanceLinkMultiplier = it
                                        )
                                    )
                            },
                            valueRange = 1f..1000f
                        )
                    }
                }
            }
        }
    }
}