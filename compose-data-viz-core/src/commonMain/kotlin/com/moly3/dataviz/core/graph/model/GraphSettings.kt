package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle

/**
 * Top-level container that bundles every customizable aspect of the graph.
 *
 * Each field is a focused settings group, so callers can override one slice
 * (e.g. just colors via [theme]) without touching the rest.
 *
 * Use [Default] for sensible defaults, then `.copy(...)` to tweak.
 *
 * Example:
 * ```
 * GraphSettings.Default.copy(
 *     theme = GraphTheme.Dark,
 *     selection = GraphSelectionSettings.Default.copy(scaleOnHover = 2f)
 * )
 * ```
 */
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
) {
    companion object {
        val Default = GraphSettings()
    }
}