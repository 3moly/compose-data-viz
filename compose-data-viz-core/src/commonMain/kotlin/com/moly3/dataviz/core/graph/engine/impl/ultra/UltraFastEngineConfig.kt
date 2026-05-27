package com.moly3.dataviz.core.graph.engine.impl.ultra

import androidx.compose.runtime.Stable

/**
 * Tunables for [UltraFastEngine].
 *
 * These control the *behavior* of the simulation itself (heat / decay / sleep /
 * timestep). They are intentionally separate from [GraphViewSettings], which
 * controls the *physics* (forces, distances, sizes).
 *
 * Think of it this way:
 *   - GraphViewSettings = "how strongly do nodes push/pull?"
 *   - UltraFastEngineConfig = "how energetic and persistent is the simulation?"
 *
 * The [Default] preset is tuned to feel like Obsidian's graph view:
 * gentle on incremental changes, settles slowly, never shocks the user.
 */
@Stable
data class UltraFastEngineConfig(
    // ---------------------------------------------------------------------
    // Heat (alpha) lifecycle
    // ---------------------------------------------------------------------

    /**
     * Initial alpha when the engine is first reheated (e.g. big structural
     * change or explicit reheat() call). Higher = bigger reshuffling.
     * Obsidian-like: ~0.3
     */
    val startAlpha: Float = 0.2f,
    val reheatAlpha: Float = 0.3f,

    /**
     * Soft wake intensity for small disturbances: single-node adds,
     * settings tweaks, post-drag settle. Should be small enough that
     * existing layout is preserved, large enough that visible motion
     * occurs over ~1-2 seconds.
     */
    val nudgeAlpha: Float = 0.1f,

    /**
     * Tiny reheat applied while a node is being dragged, just to make sure
     * the rest of the graph reacts to it.
     */
    val dragReheatAlpha: Float = 0.01f,

    /**
     * Reheat applied when group membership changes (after the first
     * initialization). Set to 0f to disable.
     */
    val groupChangeReheatAlpha: Float = 0.2f,

    /**
     * Base decay rate per step. Smaller = longer settle. Obsidian uses
     * roughly 0.0228 ( = 1 - 0.001^(1/300), i.e. cools to alpha=0.001 in
     * ~300 steps ≈ 5s at 60fps).
     *
     * If [adaptiveDecayByNodeCount] is true, this is scaled per syncData()
     * by node count (more nodes → slower decay).
     */
    val baseAlphaDecay: Float = 0.0228f,

    /** If true, alphaDecay is rescaled by node count in syncData(). */
    val adaptiveDecayByNodeCount: Boolean = true,

    /** Lower bound on alphaDecay when adaptive scaling is on. */
    val minAlphaDecay: Float = 0.005f,

    /** Upper bound on alphaDecay when adaptive scaling is on. */
    val maxAlphaDecay: Float = 0.025f,

    // ---------------------------------------------------------------------
    // Sleep / freeze
    // ---------------------------------------------------------------------

    /**
     * Kinetic-energy threshold below which the engine considers itself
     * idle (combined with low alpha). Lower = sim runs longer before
     * snapping to sleep. Raise this if you see jitter at rest.
     */
    val sleepEnergyThreshold: Float = 0.3f,

    /**
     * Alpha threshold for entering sleep state. Below this AND below
     * [sleepEnergyThreshold] in KE, the engine snaps alpha to 0.
     * Was 0.05 in the original — that killed motion too eagerly.
     */
    val sleepAlphaThreshold: Float = 0.005f,

    /**
     * The "alpha < X" check used by isAsleep to early-exit work. Decoupled
     * from snap-freeze so we can have a wider "asleep enough to skip work"
     * window without prematurely freezing positions.
     */
    val asleepAlphaCheck: Float = 0.02f,

    /**
     * When freezing is DISABLED, keep alpha at least this high so the
     * simulation never fully dies. Useful for "live" visualization modes.
     */
    val minAlphaWhenUnfrozen: Float = 0.05f,

    // ---------------------------------------------------------------------
    // Incremental-change behavior (the "no shuffle on add" knobs)
    // ---------------------------------------------------------------------

    /**
     * Adds of this many nodes or fewer use [nudgeAlpha] instead of
     * a full reheat. Default 2 means single-add/single-remove is gentle.
     */
    val gentleAddThreshold: Int = 2,

    /**
     * Fraction of node count above which a structural change counts as
     * "big" and gets a full [reheatAlpha]. Between [gentleAddThreshold]
     * and this, a moderate [moderateChangeAlpha] is used.
     */
    val bigChangeFraction: Float = 0.10f,

    /** Heat applied for "moderate" structural changes (>gentle, <big). */
    val moderateChangeAlpha: Float = 0.2f,

    // ---------------------------------------------------------------------
    // Integration / stability
    // ---------------------------------------------------------------------

    /**
     * Default damping when settings.dampingFactor == 0. Should match a
     * sane physical value — 0.85-0.92 is typical.
     */
    val fallbackDamping: Float = 0.9f,

    /** Hard upper bound on damping to prevent over-clamping. */
    val maxDamping: Float = 0.99f,

    /**
     * Adaptive timestep schedule by alpha. Hotter sim = bigger steps for
     * faster convergence; cooler sim = smaller for stability.
     */
    val timestepHot: Float = 0.15f,    // alpha > 0.5
    val timestepWarm: Float = 0.12f,   // alpha > 0.2
    val timestepCool: Float = 0.10f,   // alpha > 0.1
    val timestepCold: Float = 0.08f,   // otherwise

    /**
     * Position update is `velocity * (posBlend + alpha * alphaScale)`.
     * Keeps motion gentle when cool, snappy when hot.
     */
    val posUpdateBlend: Float = 0.4f,
    val posUpdateAlphaScale: Float = 0.3f,

    // ---------------------------------------------------------------------
    // Barnes-Hut / quadtree
    // ---------------------------------------------------------------------

    /** Quadtree θ for graphs above [bigGraphNodeCount] nodes. */
    val thetaLargeGraph: Float = 1.5f,

    /** Quadtree θ for smaller graphs (more accurate, slower). */
    val thetaSmallGraph: Float = 1.2f,

    /** Node count above which the "large graph" θ kicks in. */
    val bigGraphNodeCount: Int = 1000,

    /** Hub expansion is clamped to this maximum multiplier. */
    val maxHubScale: Float = 4f,

    // ---------------------------------------------------------------------
    // Anti-stick
    // ---------------------------------------------------------------------

    /**
     * Distance (in units of circleSize) at which anti-stick force activates
     * for two connected nodes that are nearly overlapping.
     */
    val antiStickDistanceMultiplier: Float = 2f,

    /** Force scale for anti-stick relative to base repulsion. */
    val antiStickForceMultiplier: Float = 10f,
    /** Repulsion never drops below this effective alpha, so overlapping nodes
     *  always separate even when the graph is cold. Keep small (~0.02–0.05). */
    val minRepelAlpha: Float = 0.03f,

    /** Random spread for nodes that spawn with no position. Was hardcoded 10f. */
    val spawnSpread: Float = 10f,
    /** Pairs closer than circleSize * this count as "overlapping" (sleep gate). */
    val overlapDistanceMultiplier: Float = 0.9f,
    /** Heat floor held while any overlap remains, so cold-stacked nodes still move. */
    val overlapResolveAlpha: Float = 0.00f,
    /** Spiral step for separating nodes that loaded on identical positions. */
    val deStackRadius: Float = 2f,

// Smoothness — caps motion per frame regardless of forces/node count
// ---------------------------------------------------------------------

    /**
     * Hard cap on how far ANY node may move in a single frame, in world units.
     * This is the single most important smoothness knob: it prevents the
     * "5-node graph teleports to equilibrium in 2 frames" problem without
     * needing to detune forces. Set high enough that dragged-node tracking
     * isn't laggy (the drag override bypasses this cap anyway).
     *
     * Typical: 8-15 units. Higher = snappier, lower = more cinematic.
     */
    val maxDisplacementPerFrame: Float = 8f, // was 12f

    /**
     * Velocity smoothing factor. Each frame, applied velocity is blended
     * between the previous applied velocity and the newly-integrated one:
     *   v_applied = lerp(v_prev_applied, v_new, velocitySmoothing)
     * 1.0 = no smoothing (raw physics). 0.3 = heavy smoothing.
     * 0.5-0.7 reads as "buttery" without feeling laggy.
     */
//    val velocitySmoothing: Float = 0.6f,

    /**
     * Adaptive sub-stepping. When per-frame displacement would exceed
     * maxDisplacementPerFrame, the frame is split into N sub-steps so motion
     * is integrated smoothly rather than clamped to a hard cap (which looks
     * like a stutter). Capped to prevent runaway CPU on tiny graphs.
     */
    val maxSubSteps: Int = 6,

    /**
     * Sub-step CPU budget: graphs with more than this many nodes never sub-step
     * beyond 1, regardless of displacement. Sub-stepping is for smoothness on
     * small graphs; large graphs have enough nodes to look smooth naturally.
     */
    val subStepNodeCeiling: Int = 80,

// ---------------------------------------------------------------------
// Partial freeze (isMoving = false + drag)
// ---------------------------------------------------------------------

    /**
     * When isMoving = false but a node IS being dragged, this many hops of
     * connected neighbors are allowed to move. 1 = direct neighbors only,
     * 2 = neighbors-of-neighbors, etc. 0 = only the dragged node moves.
     */
    val dragNeighborhoodHops: Int = 2,

    /**
     * Heat applied to the frozen-but-drag-active sub-simulation. Keeps the
     * local neighborhood responsive without disturbing the wider frozen layout.
     */
    val partialDragAlpha: Float = 0.15f,

// ---------------------------------------------------------------------
// Anti-clump (spread dense clusters)
// ---------------------------------------------------------------------

    /**
     * When N nodes are within circleSize * this radius of a single node,
     * extra spreading force activates. Prevents physics from converging
     * many nodes onto one centroid.
     */
    val clumpDetectRadiusMul: Float = 3f,

    /**
     * If more than this many neighbors are inside the clump radius, the
     * node is considered "clumped" and gets a radial spreading force.
     */
    val clumpNeighborThreshold: Int = 6,

    /**
     * Strength of the radial spreading force applied to clumped nodes.
     * Scales with alpha like everything else, so it never fights a settled layout.
     */
    val clumpSpreadForce: Float = 0.5f,
    val globalMotionScale: Float = 0.5f,
    val velocitySmoothing: Float = 0.15f,
) {
    companion object {
        /** Drop-in replacement matching the original hardcoded values. */
        val Default = UltraFastEngineConfig()

        /**
         * Snappier preset for small graphs / demos.
         * Faster initial layout, faster settle.
         */
        val Snappy = UltraFastEngineConfig(
            reheatAlpha = 0.5f,
            baseAlphaDecay = 0.04f,
            sleepEnergyThreshold = 1.0f,
            sleepAlphaThreshold = 0.02f,
        )

        /**
         * Extra-gentle preset. Single-node adds barely move existing layout.
         * Good when users are actively interacting with a stable graph.
         */
        val Gentle = UltraFastEngineConfig(
            reheatAlpha = 0.2f,
            nudgeAlpha = 0.05f,
            baseAlphaDecay = 0.015f,
            gentleAddThreshold = 5,
            bigChangeFraction = 0.25f,
            moderateChangeAlpha = 0.1f,
        )
    }
}