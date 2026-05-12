package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
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