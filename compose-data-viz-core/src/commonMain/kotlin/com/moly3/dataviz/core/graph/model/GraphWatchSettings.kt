package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Appearance of the ring drawn around the "watched" node (the one being focused/followed).
 *
 * The ring uses [GraphTheme.accentColor] as its color.
 */
@Immutable
@Serializable
data class GraphWatchSettings(
    /** Ring radius as a multiplier of the node's own radius. */
    val radiusMultiplier: Float = 1.5f,
    /** Stroke width of the ring, in world units (renderer divides by zoom for screen-consistent thickness). */
    val strokeWidth: Float = 4f,
) {
    companion object {
        val Default = GraphWatchSettings()
    }
}