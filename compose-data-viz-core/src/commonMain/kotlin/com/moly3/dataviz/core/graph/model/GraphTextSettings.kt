package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable

/**
 * Node label rendering settings.
 *
 * Two label flavours exist:
 * - **normal labels** drawn under each visible node (uses [normalFontSizeSp])
 * - **active label** the prominent pill shown for the currently hovered/dragged node (uses [activeFontSize])
 */
@Immutable
@Serializable
data class GraphTextSettings(
    val normalFontSizeSp: Float = 5f,
    val activeFontSizePx: Float = 24f,
    val labelPaddingDp: Float = 16f,

    // --- NEW: Zoom Scaling Properties ---
    /** Whether normal node labels should visually scale up/down as the user zooms. */
    val scaleLabelsWithZoom: Boolean = true,
    /** Minimum scale factor for text to prevent it from becoming unreadable. */
    val minLabelScale: Float = 0.6f,
    /** Maximum scale factor for text to prevent it from covering the screen or pixelating. */
    val maxLabelScale: Float = 2.0f,

    val maxLabelsVisible: Int = 30,
    val visibilityZoomThreshold: Float = 0.5f,
    val visibilityZoomFadeWidth: Float = 0.5f,

    val activePillPaddingX: Float = 24f,
    val activePillPaddingY: Float = 12f,
    val activePillCornerRadius: Float = 24f,
    val activePillBackgroundAlpha: Float = 0.85f,

    val labelMaxWidth: Int = Int.MAX_VALUE,
    val labelMaxLines: Int = Int.MAX_VALUE,


) {
    companion object {
        val Default = GraphTextSettings()
    }
}