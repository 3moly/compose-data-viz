package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Controls how hover/drag selection affects the rest of the graph
 * (dimming, scale-up, fade timings).
 *
 * Animation rates are in units-per-second so transitions stay frame-rate independent.
 */
@Immutable
@Serializable
data class GraphSelectionSettings(
    /** Multiplier applied to the active node's radius (1f = no change). */
    val scaleOnHover: Float = 1.5f,
    /** Duration in ms for the active node's scale-up tween. */
    val scaleAnimationMs: Int = 160,
    /** Duration in ms for the "selection-active" amount (drives edge dimming). */
    val selectionActiveAnimationMs: Int = 180,
    /** Alpha applied to unrelated nodes while a selection is active. */
    val fadedNodeAlpha: Float = 0.15f,
    /** Alpha applied to unrelated edges while a selection is active. */
    val fadedEdgeAlpha: Float = 0.05f,
    /** Text alpha for the active node + its connected neighbours. */
    val selectedTextAlpha: Float = 1.0f,
    /** Text alpha for nodes unrelated to the active selection. */
    val unrelatedTextAlpha: Float = 0.3f,
    /** Per-second rate for fade-in animations. */
    val fadeInRatePerSec: Float = 6.0f,
    /** Per-second rate for fade-out animations. */
    val fadeOutRatePerSec: Float = 5.0f,
    /** Per-second rate for the node dim factor transition. */
    val nodeDimRatePerSec: Float = 7.0f,
    /** Per-second rate for the edge highlight transition (currently unused; reserved). */
    val edgeFadeRatePerSec: Float = 6.0f,
) {
    companion object {
        val Default = GraphSelectionSettings()
    }
}
