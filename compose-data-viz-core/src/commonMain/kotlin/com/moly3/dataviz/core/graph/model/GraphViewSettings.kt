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
    val circleQuality: Float = 0.001f,

    /** Border thickness as fraction of radius (0f = no border, 0.1f = 10% border). */
    val circleBorderWidth: Float = 0.1f,

    /** Border color. Null = use node color darkened. */
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
) {
    companion object {
        val Default = GraphViewSettings(
            centerForce = 0.0088f,
            linkForce = 10f,
            linkDistance = 100f,
            repelForce = 20000f,
            circleSize = 10f,
            connectedRepulsionMultiplier = 0.3f,
            mutualConnectionRepulsionMultiplier = 0.05f,
            unconnectedRepulsionMultiplier = 1.0f,
            longDistanceLinkMultiplier = 1f,
            clusteringForce = 1f,
            minMutualConnectionsForClustering = 10,
            maxForce = 15f,
            maxConnectionsForFullProcessing = 100,
            spatialOptimizationThreshold = 50,
            circleSizeMultiplier = null
        )
    }
}