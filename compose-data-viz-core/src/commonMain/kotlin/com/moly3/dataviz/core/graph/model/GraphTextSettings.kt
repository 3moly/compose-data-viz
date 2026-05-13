package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Node label rendering settings.
 *
 * Two label flavours exist:
 * - **normal labels** drawn under each visible node (uses [normalFontSize])
 * - **active label** the prominent pill shown for the currently hovered/dragged node (uses [activeFontSize])
 */
@Immutable
data class GraphTextSettings(
    /** Font size for the regular labels under every visible node. */
    val normalFontSize: TextUnit = 12.sp,
    /** Font size in *pixels* for the active node's prominent label. Applied via density.toSp. */
    val activeFontSizePx: Float = 24f,

    /** Vertical gap between a node's edge and its label, in dp-converted px. */
    val labelPaddingDp: Float = 16f,

    /** Hard cap on how many node labels render simultaneously (closest-to-center wins). */
    val maxLabelsVisible: Int = Int.MAX_VALUE,
    /** Zoom level below which normal labels stop being drawn. */
    val visibilityZoomThreshold: Float = 0.5f,
    /** Width of the zoom range over which labels fade in (from [visibilityZoomThreshold] upward). */
    val visibilityZoomFadeWidth: Float = 0.5f,

    /** Horizontal padding inside the active node's pill background. */
    val activePillPaddingX: Float = 24f,
    /** Vertical padding inside the active node's pill background. */
    val activePillPaddingY: Float = 12f,
    /** Corner radius of the active node's pill background. */
    val activePillCornerRadius: Float = 24f,
    /** Opacity of the active node's pill background (multiplied by selection alpha). */
    val activePillBackgroundAlpha: Float = 0.85f,
) {
    companion object {
        val Default = GraphTextSettings()
    }
}