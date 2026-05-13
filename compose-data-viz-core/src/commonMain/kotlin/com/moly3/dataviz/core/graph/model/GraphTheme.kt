package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * All colors used by the graph, grouped in one place.
 *
 * - [nodeColor] — default fill for a node when it has no `colorValue` of its own
 * - [nodeOutlineColor] — fill used for the rim/halo around nodes (also used as edge color base)
 * - [edgeColor] — color for unselected connection lines (falls back to [nodeOutlineColor] if null)
 * - [accentColor] — highlight color for selected edges and the watch-node ring
 * - [textColor] — font color for all node labels
 * - [draggedNodeColor] / [hoveredNodeColor] — overrides applied while a node is being dragged or hovered
 * - [activeLabelBackgroundLight] / [activeLabelBackgroundDark] — pill background behind the active node's label.
 *   The right one is picked automatically based on [textColor] luminance.
 */
@Immutable
data class GraphTheme(
    val nodeColor: Color,
    val nodeOutlineColor: Color,
    val edgeColor: Color?,
    val accentColor: Color,
    val textColor: Color,
    val draggedNodeColor: Color = Color(0xFF4CAF50),
    val hoveredNodeColor: Color = Color(0xFF4CAF50),
    val activeLabelBackgroundLight: Color = Color(0xFFF5F5F5),
    val activeLabelBackgroundDark: Color = Color(0xFF1E1E1E),
) {
    /** Resolved edge color (falls back to [nodeOutlineColor] when [edgeColor] is null). */
    val resolvedEdgeColor: Color get() = edgeColor ?: nodeOutlineColor

    companion object {

        val Dark = GraphTheme(
            nodeColor = Color(0xFF8A8A8A),
            nodeOutlineColor = Color(0xFF4A4A4A),
            edgeColor = Color(0xFF4A4A4A),
            accentColor = Color(0xFF7E57C2),
            textColor = Color(0xFFE0E0E0),
        )

        val Light = GraphTheme(
            nodeColor = Color(0xFF606060),
            nodeOutlineColor = Color(0xFFBDBDBD),
            edgeColor = Color(0xFFBDBDBD),
            accentColor = Color(0xFF5E35B1),
            textColor = Color(0xFF212121),
        )

        val Default = Dark
    }
}