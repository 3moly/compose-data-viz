package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import com.moly3.dataviz.core.graph.hull.GroupSettings

@Immutable
data class GraphSettings(
    val view: GraphViewSettings = GraphViewSettings.Default,
    val theme: GraphTheme = GraphTheme.Default,
    val selection: GraphSelectionSettings = GraphSelectionSettings.Default,
    val edge: GraphEdgeSettings = GraphEdgeSettings.Default,
    val text: GraphTextSettings = GraphTextSettings.Default,
    val zoom: GraphZoomSettings = GraphZoomSettings.Default,
    val watch: GraphWatchSettings = GraphWatchSettings.Default,
    val textStyle: TextStyle = TextStyle.Default,
    val groupSettings: GroupSettings = GroupSettings()
) {
    companion object {
        val Default = GraphSettings()
    }
}