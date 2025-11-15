package com.moly3.dataviz.graph.model

import kotlinx.serialization.Serializable

@Serializable
data class GraphViewSettings(
    val centerForce: Float,
    val linkForce: Float,
    val linkDistance: Float,
    val repelForce: Float,
    val circleSize: Float,
    val connectedRepulsionMultiplier: Float,
    val mutualConnectionRepulsionMultiplier: Float,
    val unconnectedRepulsionMultiplier: Float,
    val longDistanceLinkMultiplier: Float,
    val clusteringForce: Float,
    val minMutualConnectionsForClustering: Int,
    val maxForce: Float,
    val dampingFactor: Float = 0.92f,
    val maxConnectionsForFullProcessing: Int = 100,
    val spatialOptimizationThreshold: Int = 50
) {
    companion object {
        val Default = GraphViewSettings(
            centerForce = 0.0088f, // Your original
            linkForce = 10f,       // Your original
            linkDistance = 100f,   // Your original
            repelForce = 20000f,   // Your original
            circleSize = 10f,      // Your original

            connectedRepulsionMultiplier = 0.3f,      // Your original
            mutualConnectionRepulsionMultiplier = 0.05f, // Your original
            unconnectedRepulsionMultiplier = 1.0f,    // Your original

            longDistanceLinkMultiplier = 1f,      // Your original
            clusteringForce = 1f,                 // Your original
            minMutualConnectionsForClustering = 10, // Your original
            maxForce = 15f,                       // Your original

            // Performance tweaks
            maxConnectionsForFullProcessing = 100,
            spatialOptimizationThreshold = 50
        )
    }
}
//@Serializable
//data class GraphViewSettings(
//    val centerForce: Float,
//    val linkForce: Float,
//    val linkDistance: Float,
//    val repelForce: Float,
//    val circleSize: Float,
//    val connectedRepulsionMultiplier: Float,
//    val mutualConnectionRepulsionMultiplier: Float,
//    val unconnectedRepulsionMultiplier: Float,
//    val longDistanceLinkMultiplier: Float,
//    val clusteringForce: Float,
//    val minMutualConnectionsForClustering: Int,
//    val maxForce: Float,
//    // New stability parameters
//    val dampingFactor: Float = 0.92f,
//    val maxConnectionsForFullProcessing: Int = 200,
//    val spatialOptimizationThreshold: Int = 50
//) {
//    companion object {
//        val Default = GraphViewSettings(
//            centerForce = 0.005f, // Reduced for stability
//            linkForce = 8f, // Slightly reduced
//            linkDistance = 120f, // Increased for better spacing
//            repelForce = 15000f, // Reduced to prevent excessive forces
//            circleSize = 10f,
//
//            connectedRepulsionMultiplier = 0.25f,
//            mutualConnectionRepulsionMultiplier = 0.03f,
//            unconnectedRepulsionMultiplier = 1.0f,
//
//            longDistanceLinkMultiplier = 1.2f,
//            clusteringForce = 0.8f,
//            minMutualConnectionsForClustering = 5,
//            maxForce = 12f, // Reduced max force
//
//            // New parameters for stability
//            dampingFactor = 0.92f,
//            maxConnectionsForFullProcessing = 200,
//            spatialOptimizationThreshold = 50
//        )
//    }
//}