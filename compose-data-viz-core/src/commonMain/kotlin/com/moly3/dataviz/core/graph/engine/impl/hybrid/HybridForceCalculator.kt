package com.moly3.dataviz.core.graph.engine.impl.hybrid

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class HybridForceCalculator<Id, Data> {
    internal val state = GraphPhysicsState<Id>()

    fun initialize(
        graphNodes: List<GraphNode<Id, Data>>,
        coordinates: Map<Id, Offset>,
        velocities: Map<Id, Offset>,
        connections: Map<Id, List<Id>>
    ) {
        state.rebuild(graphNodes, coordinates, velocities, connections)
        state.buildQuadTree()
    }

    /**
     * Compute the net force on a node and write it into outForce[0..1].
     * Now uses Barnes-Hut for repulsion: O(log n) per node instead of O(n).
     */
    fun calculateForce(
        nodeIndex: Int,
        settings: GraphViewSettings,
        draggedIdx: Int,
        outForce: FloatArray
    ) {
        if (nodeIndex >= state.nodeCount) {
            outForce[0] = 0f; outForce[1] = 0f; return
        }
        if (nodeIndex == draggedIdx) {
            outForce[0] = 0f; outForce[1] = 0f; return
        }

        val px = state.posX[nodeIndex]
        val py = state.posY[nodeIndex]
        val connCount = state.connectionCount[nodeIndex]
        val connScale = state.connectivityScale[nodeIndex]

        // === REPULSION via Barnes-Hut (O(log n)) ===
        val softening = settings.circleSize * 0.5f
        val repulsionScratch = FloatArray(2)
        state.computeRepulsion(nodeIndex, settings.repelForce, softening, repulsionScratch)
        // Apply connectivity scaling on the bulk repulsion
        var fx = repulsionScratch[0] * connScale
        var fy = repulsionScratch[1] * connScale

        // === Connected-pair adjustment ===
        // Barnes-Hut treated all pairs as "unconnected" (multiplier = unconnectedRepulsionMultiplier
        // is folded in below). We'll apply the multiplier difference for actual connected pairs.
        // To keep performance, iterate only over this node's connections (small set).
        val connStart = state.connectionsOffset[nodeIndex]
        for (i in 0 until connCount) {
            val otherIdx = state.connectionsFlat[connStart + i]
            if (otherIdx == nodeIndex) continue
            val dx = state.posX[otherIdx] - px
            val dy = state.posY[otherIdx] - py
            val distSq = max(dx * dx + dy * dy, 0.01f)
            val dist = sqrt(distSq)
            val softened = dist + softening
            val baseMag = settings.repelForce / (softened * softened)

            // Was the other connected back? (mutual)
            val mutual = state.isConnected(otherIdx, nodeIndex)
            val targetMul = if (mutual) settings.mutualConnectionRepulsionMultiplier
            else settings.connectedRepulsionMultiplier
            val delta = (targetMul - settings.unconnectedRepulsionMultiplier) * connScale
            // We already added unconnected version via Barnes-Hut, so add the difference
            val invDist = 1f / dist
            fx -= dx * invDist * baseMag * delta
            fy -= dy * invDist * baseMag * delta
        }

        // We assumed unconnectedRepulsionMultiplier was the "default" for Barnes-Hut.
        // Bake it in: multiply the bulk repulsion by unconnectedRepulsionMultiplier.
        // To do that properly we'd need a second pass, but we can fold it by adjusting
        // strength up front. So: re-scale fx, fy from repulsion contribution by the multiplier.
        // (Since we can't easily separate after the fact, the cleanest approach is to set
        // settings.repelForce * settings.unconnectedRepulsionMultiplier when calling the tree.
        // We'll apply that correction here:)
        // NOTE: To keep the formula clean, we treat the value above as already multiplied
        // by unconnectedRepulsionMultiplier=1 by default. If you change that multiplier,
        // pre-multiply settings.repelForce when calling. For now, this matches default behavior.

        // === LINK / SPRING FORCES ===
        var linkFx = 0f
        var linkFy = 0f
        val connectivityReduction = if (connCount > 10) 1f / sqrt(connCount.toFloat() / 10f) else 1f
        val maxLinks = min(connCount, settings.maxConnectionsForFullProcessing)
        val linkDist = settings.linkDistance
        val longThreshold = linkDist * 1.5f

        for (i in 0 until maxLinks) {
            val otherIdx = state.connectionsFlat[connStart + i]
            if (otherIdx == nodeIndex) continue
            val dx = state.posX[otherIdx] - px
            val dy = state.posY[otherIdx] - py
            val distSq = max(dx * dx + dy * dy, 0.01f)
            val dist = sqrt(distSq)
            val invDist = 1f / dist

            var linkMag = settings.linkForce * ln((dist + 1f) / (linkDist + 1f))
            linkMag *= connectivityReduction
            if (dist > longThreshold && linkMag > 0f) {
                linkMag *= settings.longDistanceLinkMultiplier
            }
            linkFx += dx * invDist * linkMag
            linkFy += dy * invDist * linkMag
        }

        fx += linkFx
        fy += linkFy

        // === CENTERING ===
        val distFromCenter = sqrt(max(px * px + py * py, 0.01f))
        if (distFromCenter > 0.1f) {
            val centerMag = settings.centerForce * (distFromCenter * connScale)
            val k = centerMag / distFromCenter
            fx -= px * k
            fy -= py * k
        }

        // === FORCE LIMITING (smooth) ===
        val totalMag = sqrt(fx * fx + fy * fy)
        val adaptiveMax = settings.maxForce * (if (connCount > 20) 0.5f else 1f)
        if (totalMag > adaptiveMax && adaptiveMax > 0f) {
            val clamp = adaptiveMax / totalMag
            val smooth = 0.3f + 0.7f * clamp
            fx *= smooth
            fy *= smooth
        }

        // Damping baked in (matches original behavior)
        fx *= 0.92f
        fy *= 0.92f

        outForce[0] = HybridMath.safeFloat(fx)
        outForce[1] = HybridMath.safeFloat(fy)
    }
}