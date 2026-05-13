package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable

/**
 * Edge (connection line) rendering settings.
 *
 * Stroke widths are specified in *world* units; the renderer scales them by
 * `1 / zoom` so lines stay visually consistent at any zoom level.
 */
@Immutable
data class GraphEdgeSettings(
    /** Base stroke width for normal (unselected) edges, in world units before zoom compensation. */
    val strokeWidth: Float = 2f,
    /** Extra stroke width added on top of [strokeWidth] for edges touching the active node. */
    val strokeHighlightBonus: Float = 6f,
    /** Zoom level below which edges stop being drawn (perf optimisation). */
    val visibilityZoomThreshold: Float = 0.15f,
) {
    companion object {
        val Default = GraphEdgeSettings()
    }
}