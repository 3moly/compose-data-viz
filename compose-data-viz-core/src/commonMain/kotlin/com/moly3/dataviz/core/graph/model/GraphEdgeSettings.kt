package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * How a size value reacts to camera zoom.
 *
 * The same policy concept applies to anything specified in pixels that the
 * renderer might want to convert into world space: arrow head dimensions,
 * dash/dot patterns, stroke widths, etc.
 *
 * Mental model — what does the user SEE as they zoom in?
 *
 *  - [ScreenConstant]: value stays the same size on screen at every zoom.
 *      Best for UI affordances (arrow heads, dot patterns) where the user
 *      cares about readability, not "this is a 12px arrow in world space".
 *      Implementation: divide by zoom.
 *
 *  - [WorldConstant]: value is fixed in world space and visually grows when
 *      zooming in, shrinks when zooming out. Use when the value should feel
 *      "attached to" the graph (e.g. arrows that are genuinely part of the
 *      diagram). Implementation: pass through unchanged.
 *
 *  - [Clamped]: behaves like [ScreenConstant] (1/zoom) but bounds the
 *      effective scale to [minScale]..[maxScale]. Inside the band it grows
 *      with zoom (so very zoomed-out views don't get microscopic arrows)
 *      and outside it stops growing/shrinking. This is the most forgiving
 *      default for "looks good at any zoom" without surprise gigantism at
 *      extreme zoom-ins.
 */
@Immutable
@Serializable
sealed class ZoomScalePolicy {

    /** Constant on-screen size. `effective = base / zoom`. */
    @Immutable
    @Serializable
    data object ScreenConstant : ZoomScalePolicy()

    /** Constant world-space size. `effective = base`. */
    @Immutable
    @Serializable
    data object WorldConstant : ZoomScalePolicy()

    /**
     * Screen-constant within a zoom band, then clamped.
     *
     * `effective = base / clamp(zoom, minScale, maxScale)`
     *
     * Example: [minScale] = 0.5, [maxScale] = 2.0 means the value's effective
     * world size grows as you zoom out (until zoom = 0.5) and shrinks as you
     * zoom in (until zoom = 2.0), and stops responding outside that range.
     * Net effect on screen: visually constant inside the band, visually
     * shrinks when zoomed out below it, visually grows when zoomed in above
     * it. Good for keeping arrows legible at all zooms without exploding.
     */
    @Immutable
    @Serializable
    data class Clamped(
        val minScale: Float = 0.5f,
        val maxScale: Float = 2.0f,
    ) : ZoomScalePolicy()
}

/**
 * Resolve a base value into a world-space value the renderer can use directly.
 *
 * Inputs:
 *   [base]   value as authored, in pixels (see [GraphEdgeSettings] doc).
 *   [zoom]   current camera zoom; > 1 = zoomed in, < 1 = zoomed out.
 *
 * Output: the value to pass to `drawLine`, `drawPath`, etc. inside the
 * world-space transformed canvas.
 *
 * Inlined to keep this allocation-free on the per-edge hot path. The when
 * over a sealed class compiles to a small jump table — no allocations,
 * no virtual calls.
 */
inline fun ZoomScalePolicy.resolve(base: Float, zoom: Float): Float {
    val z = if (zoom < 0.0001f) 0.0001f else zoom
    return when (this) {
        ZoomScalePolicy.ScreenConstant -> base / z
        ZoomScalePolicy.WorldConstant  -> base
        is ZoomScalePolicy.Clamped -> {
            val clampedZoom = when {
                z < minScale -> minScale
                z > maxScale -> maxScale
                else -> z
            }
            base / clampedZoom
        }
    }
}
/**
 * Edge (connection line) rendering settings.
 *
 * Stroke widths are specified in *world* units; the renderer scales them by
 * `1 / zoom` so lines stay visually consistent at any zoom level.
 */
@Immutable
@Serializable
data class GraphEdgeSettings(
    /** Base stroke width for normal (unselected) edges, in world units before zoom compensation. */
    val strokeWidth: Float = 2f,
    /** Extra stroke width added on top of [strokeWidth] for edges touching the active node. */
    val strokeHighlightBonus: Float = 6f,
    /** Zoom level below which edges stop being drawn (perf optimisation). */
    val visibilityZoomThreshold: Float = 0.15f,
    val arrowHeadLengthPx: Float = 12f,
    val arrowHeadWidthPx: Float = 10f,
    val dashOnPx: Float = 12f,
    val dashOffPx: Float = 8f,
    val dotOnPx: Float = 2f,
    val dotOffPx: Float = 6f,
    // ---- Zoom-response policies ------------------------------------------

    /**
     * How [strokeWidth] / [strokeHighlightBonus] react to zoom.
     * Default: constant on-screen thickness (matches legacy behavior).
     */
    val strokeScalePolicy: ZoomScalePolicy = ZoomScalePolicy.WorldConstant,

    /**
     * How [arrowHeadLengthPx] / [arrowHeadWidthPx] react to zoom.
     * Default: constant on-screen size (matches legacy behavior, keeps arrows
     * readable at every zoom).
     *
     * Set to [ZoomScalePolicy.WorldConstant] if you want arrows that grow
     * visually with the graph. Set to [ZoomScalePolicy.Clamped] for a
     * hybrid that's screen-constant in a usable band but stops growing at
     * extreme zooms.
     */
    val arrowHeadScalePolicy: ZoomScalePolicy = ZoomScalePolicy.WorldConstant,

    /**
     * How dash/dot pattern lengths react to zoom. Matches the previous
     * `/ zoom` behavior so the visual cadence stays constant.
     */
    val dashPatternScalePolicy: ZoomScalePolicy = ZoomScalePolicy.WorldConstant,
) {
    companion object {
        val Default = GraphEdgeSettings()
    }
}