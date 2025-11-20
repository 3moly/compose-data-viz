package com.threemoly.sample.graph

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

data class GraphState(
    val isShowSettings: Boolean = false,
    val config: GraphSettingsConfig = GraphSettingsConfig.Default,
    val graphNodes: ImmutableList<ObsidianGraphNode> = persistentListOf(),
    val connections: ImmutableMap<String, ImmutableList<String>> = persistentMapOf(),
    val zoom: Float = 1f,
    val graphUserPosition: Offset = Offset.Zero,
    val graphViewSettings: GraphViewSettings = GraphViewSettings.Default,
    val coordinates: ImmutableMap<String, Offset> = persistentMapOf(),
    val velocities: ImmutableMap<String, Offset> = persistentMapOf(),
)