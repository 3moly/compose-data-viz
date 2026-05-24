package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import com.moly3.dataviz.core.graph.hull.GroupSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Immutable
@Serializable
data class GraphSettings(
    val view: GraphViewSettings = GraphViewSettings.Default,
    val theme: GraphTheme = GraphTheme.Default,
    val selection: GraphSelectionSettings = GraphSelectionSettings.Default,
    val edge: GraphEdgeSettings = GraphEdgeSettings.Default,
    val text: GraphTextSettings = GraphTextSettings.Default,
    val zoom: GraphZoomSettings = GraphZoomSettings.Default,
    val watch: GraphWatchSettings = GraphWatchSettings.Default,
    val groupSettings: GroupSettings = GroupSettings(),
    val isMoving: Boolean = true,
) {
    companion object {
        val Default = GraphSettings()
    }
}