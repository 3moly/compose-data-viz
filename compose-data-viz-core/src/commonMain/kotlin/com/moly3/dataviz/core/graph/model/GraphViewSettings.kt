package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Pure physics / simulation settings. Visual concerns live in [GraphSettings] and its sub-groups.
 *
 * Kept @Serializable because physics presets may be persisted to disk.
 */
//@Serializable
@Stable
@Serializable
data class GraphViewSettings(
    val targetFrameMs: Long = 16L,
    val cpuCores: Int? = null,
    val centerForce: Float,
    val linkForce: Float,
    val linkDistance: Float,
    val repelForce: Float,
    val circleSize: Float,
    val circleSizeMultiplier: Float?,
    /** Circle edge quality: 0f = pixelated/hard, 1f = smooth. Controls AA width. */
    val circleQuality: Float = 0.0001f,

    /** Border thickness as fraction of radius (0f = no border, 0.1f = 10% border). */
    val circleBorderWidth: Float = 0.02f,

    /** Border color. Null = use node color darkened. */
    @Serializable(with = ComposeColorSerializer::class)
    val circleBorderColor: Color? = null,
    val connectedRepulsionMultiplier: Float,
    val mutualConnectionRepulsionMultiplier: Float,
    val unconnectedRepulsionMultiplier: Float,
    val longDistanceLinkMultiplier: Float,
    val clusteringForce: Float,
    val minMutualConnectionsForClustering: Int,
    val maxForce: Float,
    val dampingFactor: Float = 0.92f,
    val maxConnectionsForFullProcessing: Int = 100,
    val spatialOptimizationThreshold: Int = 50,
    val maxTextsAtCenterVisible: Int = Int.MAX_VALUE,
    // Add to GraphViewSettings: val hubExpansionExponent: Float = 0.5f  (0 = off, 0.5 = sqrt, 1 = linear)
    val hubExpansionExponent: Float = 0.5f
) {
    companion object {
        val Default = GraphViewSettings(
            centerForce = 0.7f,  // Slightly stronger to keep centered

            linkForce = 8f,        // was 35f — way too strong, causes whip
                    repelForce = 15000f,   // was 50000f — causes explosion on add
                    dampingFactor = 0.85f, // was 0.75f — bit more glide, less stiff
            // Stronger links with more "snap"
//            linkForce = 35f,      // Increased for faster convergence
            linkDistance = 30f,   // Tighter connections

            // Higher repulsion to prevent sticking
//            repelForce = 50000f,  // Much higher to break apart stuck nodes

            circleSize = 8f,      // Slightly smaller circles

            // Better connected node handling
            connectedRepulsionMultiplier = 0.3f,  // Lower = less repulsion between connected nodes
            mutualConnectionRepulsionMultiplier = 0.05f,
            unconnectedRepulsionMultiplier = 1.0f,
            longDistanceLinkMultiplier = 2.0f,  // Pull distant connected nodes together faster

            clusteringForce = 1.5f,
            minMutualConnectionsForClustering = 3,

            maxForce = 60f,  // Higher to allow faster movement

            // IMPORTANT: Higher damping = less bounce, but too high prevents movement
//            dampingFactor = 0.75f,  // Balance between 0.65 (too stiff) and 0.92 (too bouncy)

            hubExpansionExponent = 0.3f,  // Less aggressive expansion (was 0.5)


            maxConnectionsForFullProcessing = 100,
            spatialOptimizationThreshold = 50,
            circleSizeMultiplier = null
        )
    }
}