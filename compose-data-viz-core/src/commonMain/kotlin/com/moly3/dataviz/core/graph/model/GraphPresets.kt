package com.moly3.dataviz.core.graph.model // ============================================================================
// ADAPTIVE PRESETS — tuned for graph size
// ============================================================================

/**
 * Default settings in your code (centerForce=0.0088, repelForce=20000, etc.) work well at
 * ~10–50 nodes. At 1000+ nodes the same numbers cause:
 *   - clumping (repel too weak relative to N²)
 *   - slow convergence (link force overpowered)
 *   - jittery hubs (max force too low)
 *
 * These presets scale the key parameters with graph size using empirical formulas
 * adapted from d3-force and Obsidian's defaults.
 */
object GraphPresets {
    const val SMALL_NODE_PRESET_COUNT = 50
    const val MEDIUM_NODE_PRESET_COUNT = 250
    const val LARGE_NODE_PRESET_COUNT = 1000
    const val HUGE_NODE_PRESET_COUNT = 5000

    /**
     * Pick a preset based on node count. The returned settings are tuned to give a
     * stable, readable layout in a small number of physics steps regardless of size.
     */
    fun forNodeCount(n: Int): GraphViewSettings =
        when {
            n <= SMALL_NODE_PRESET_COUNT -> small()
            n <= MEDIUM_NODE_PRESET_COUNT -> medium()
            n <= LARGE_NODE_PRESET_COUNT -> large()
            n <= HUGE_NODE_PRESET_COUNT -> huge()
            else -> massive()
        }

    /** Tiny graphs — generous spacing, gentle forces, looks clean. */
    fun small(): GraphViewSettings =
        GraphViewSettings(
            centerForce = 0.012f,
            linkForce = 8f,
            linkDistance = 90f,
            repelForce = 12000f,
            circleSize = 10f,
            connectedRepulsionMultiplier = 0.3f,
            mutualConnectionRepulsionMultiplier = 0.05f,
            unconnectedRepulsionMultiplier = 1.0f,
            longDistanceLinkMultiplier = 1f,
            clusteringForce = 1f,
            minMutualConnectionsForClustering = 10,
            maxForce = 15f,
            dampingFactor = 0.92f,
            maxConnectionsForFullProcessing = 100,
            spatialOptimizationThreshold = 50,
            circleSizeMultiplier = null,
        )

    /** Hundreds of nodes — slightly tighter, stronger center pull. */
    fun medium(): GraphViewSettings =
        small().copy(
            centerForce = 0.018f,
            linkForce = 10f,
            linkDistance = 70f,
            repelForce = 18000f,
            maxForce = 18f,
            dampingFactor = 0.90f,
        )

    /** ~1000 nodes — Obsidian's vault size. Stronger center, weaker repulsion. */
    fun large(): GraphViewSettings =
        small().copy(
            centerForce = 0.025f,
            linkForce = 12f,
            linkDistance = 55f,
            repelForce = 24000f,
            circleSize = 8f,
            connectedRepulsionMultiplier = 0.2f,
            maxForce = 22f,
            dampingFactor = 0.88f,
            maxConnectionsForFullProcessing = 60,
        )

    /** Several thousand — physics needs to be more aggressive to stay responsive. */
    fun huge(): GraphViewSettings =
        small().copy(
            centerForce = 0.035f,
            linkForce = 15f,
            linkDistance = 45f,
            repelForce = 30000f,
            circleSize = 6f,
            connectedRepulsionMultiplier = 0.15f,
            mutualConnectionRepulsionMultiplier = 0.03f,
            maxForce = 28f,
            dampingFactor = 0.85f,
            maxConnectionsForFullProcessing = 40,
            spatialOptimizationThreshold = 20,
        )

    /** 5000+ nodes — readability over physics fidelity. */
    fun massive(): GraphViewSettings =
        small().copy(
            centerForce = 0.05f,
            linkForce = 18f,
            linkDistance = 35f,
            repelForce = 35000f,
            circleSize = 5f,
            connectedRepulsionMultiplier = 0.1f,
            mutualConnectionRepulsionMultiplier = 0.02f,
            maxForce = 35f,
            dampingFactor = 0.82f,
            maxConnectionsForFullProcessing = 25,
            spatialOptimizationThreshold = 10,
        )
}
