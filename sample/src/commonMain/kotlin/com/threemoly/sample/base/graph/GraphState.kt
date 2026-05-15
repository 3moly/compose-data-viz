package com.threemoly.sample.base.graph

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.graph.func.GraphPresets
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
    val graphSettings: GraphSettings = GraphSettings.Default.copy(view = GraphPresets.massive()),
    val coordinates: ImmutableMap<String, Offset> = persistentMapOf(),
    val velocities: ImmutableMap<String, Offset> = persistentMapOf(),
)