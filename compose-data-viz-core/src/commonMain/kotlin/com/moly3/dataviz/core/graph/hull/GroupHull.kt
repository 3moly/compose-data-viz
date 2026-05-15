package com.moly3.dataviz.core.graph.hull

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * Static config controlling group magnetization (cohesion) physics and rendering.
 * Lives inside GraphViewSettings or as its own block — put it wherever your settings tree allows.
 */
@Immutable
data class GroupSettings(
    /** Master switch. */
    val enabled: Boolean = true,

    /** Strength of pull toward each group's centroid. Scales with alpha like other forces. */
    val cohesionForce: Float = 0.06f,

    /**
     * Repulsion between *centroids* of different groups, so islands stay apart.
     * 0f disables.
     */
    val groupSeparation: Float = 800f,

    /** Min distance below which inter-group repulsion saturates (avoid blowup). */
    val groupSeparationSoftening: Float = 80f,

    // ---------- Hull rendering ----------

    /** How often to recompute hulls when the graph is moving, in ms. */
    val hullRecomputeIntervalMs: Long = 120L,

    /** When the simulation is asleep, snap one final hull at this interval. */
    val hullSettledIntervalMs: Long = 600L,

    /**
     * Concave-hull "k" parameter: larger = smoother / more convex. 3..8 typical.
     * For ≤3 nodes we degrade gracefully (line / single bubble).
     */
    val hullK: Int = 5,

    /** Padding added around each node when expanding the hull (px in graph coords). */
    val hullPadding: Float = 24f,

    /** Smoothing tension for Catmull-Rom; 0.5 looks like the hand-drawn example. */
    val hullSmoothing: Float = 0.5f,

    /** Stroke width of the hull outline, in graph coordinates (gets divided by zoom on draw). */
    val hullStrokeWidth: Float = 3f,

    /** If true, fill the hull with the group color at low alpha. */
    val hullFill: Boolean = true,

    /** Multiplier applied to the group color alpha for fill. */
    val hullFillAlpha: Float = 0.10f,
)

/**
 * One computed island border. Path is in *graph* (pre-zoom) coordinates.
 */
@Immutable
data class GroupHull(
    val label: String,
    val groupId: String,
    val color: Color,
    val path: Path,
    /** Anchor for an optional label (the topmost point of the hull). */
    val labelAnchor: Offset,
)