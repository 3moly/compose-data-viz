package com.moly3.dataviz.core.graph.engine.impl.hybrid

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.func.avaliableCpuProcessors
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import kotlinx.coroutines.*
import kotlin.math.*

internal suspend fun <Id, Data> applyHybridForces(
    graphNodes: List<GraphNode<Id, Data>>,
    connections: Map<Id, List<Id>>,
    settings: GraphViewSettings,
    coordinates: MutableMap<Id, Offset>,
    velocities: MutableMap<Id, Offset>,
    draggedNode: DragNodeData<Id>?,
    calculator: HybridForceCalculator<Id, Data>,
    energyOut: FloatArray? = null  // optional: total kinetic energy for adaptive frame skipping
) = coroutineScope {
    calculator.initialize(graphNodes, coordinates, velocities, connections)
    val state = calculator.state
    val n = state.nodeCount
    if (n == 0) return@coroutineScope

    val draggedIdx = draggedNode?.id?.let { state.idToIndex[it] } ?: -1

    // Parallel chunks - sized to actual core count
    val cores = (avaliableCpuProcessors(settings.cpuCores) - 1).coerceAtLeast(1)
    val chunkSize = max(32, (n + cores - 1) / cores)
    val chunkCount = (n + chunkSize - 1) / chunkSize

    val partialEnergies = FloatArray(chunkCount)

    val jobs = (0 until chunkCount).map { chunkIdx ->
        async(Dispatchers.Default) {
            val start = chunkIdx * chunkSize
            val end = min(start + chunkSize, n)
            val force = FloatArray(2)
            var localEnergy = 0f

            for (i in start until end) {
                if (i == draggedIdx) continue
                calculator.calculateForce(i, settings, draggedIdx, force)

                // Velocity Verlet-ish integration (matches original feel with safer damping)
                val ax = force[0] * 0.1f
                val ay = force[1] * 0.1f
                var vx = (state.velX[i] + ax) * settings.dampingFactor.let {
                    if (it == 0f) 0.95f else it.coerceAtMost(0.99f)
                }
                var vy = (state.velY[i] + ay) * settings.dampingFactor.let {
                    if (it == 0f) 0.95f else it.coerceAtMost(0.99f)
                }
                // Sleep tiny velocities to avoid jitter (this is what makes it "calm down" like Obsidian)
                if (vx * vx + vy * vy < 1e-4f) {
                    vx = 0f; vy = 0f
                }

                state.velX[i] = vx
                state.velY[i] = vy
                state.posX[i] += vx * 0.5f
                state.posY[i] += vy * 0.5f

                localEnergy += vx * vx + vy * vy
            }
            partialEnergies[chunkIdx] = localEnergy
        }
    }
    jobs.awaitAll()

    if (draggedNode != null && draggedIdx >= 0) {
        draggedNode.offset?.let { draggedNodeOffset ->
            state.posX[draggedIdx] = draggedNodeOffset.x
            state.posY[draggedIdx] = draggedNodeOffset.y
        }
        state.velX[draggedIdx] = 0f
        state.velY[draggedIdx] = 0f
    }

    state.writeBack(coordinates, velocities)

    if (energyOut != null) {
        var totalE = 0f
        for (e in partialEnergies) totalE += e
        energyOut[0] = totalE
    }
}