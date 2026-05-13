package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable

/**
 * Zoom range and scroll-wheel zoom step factors.
 *
 * - Scroll up multiplies zoom by [stepIn]
 * - Scroll down multiplies zoom by [stepOut] (= `1f / stepIn` for symmetric feel)
 * - Pinch zoom is bounded by [minZoom] and [maxZoom]
 */
@Immutable
data class GraphZoomSettings(
    val minZoom: Float = 0.05f,
    val maxZoom: Float = 8f,
    val stepIn: Float = 1.1f,
    val stepOut: Float = 1f / 1.1f,
) {
    companion object {
        val Default = GraphZoomSettings()
    }
}