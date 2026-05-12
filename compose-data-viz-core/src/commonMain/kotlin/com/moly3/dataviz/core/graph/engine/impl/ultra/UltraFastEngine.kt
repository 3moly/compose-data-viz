package com.moly3.dataviz.core.graph.engine.impl.ultra

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.func.avaliableCpuProcessors
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import kotlinx.coroutines.*
import kotlin.math.*

class UltraFastEngine<Id, Data> : IGraphEngine<Id, Data> {
    private var nodeCount = 0
    private var posX = FloatArray(0)
    private var posY = FloatArray(0)
    private var velX = FloatArray(0)
    private var velY = FloatArray(0)
    private var forceX = FloatArray(0)
    private var forceY = FloatArray(0)

    private var connectionsOffset = IntArray(1)
    private var connectionsFlat = IntArray(0)
    private var degree = IntArray(0)

    private val ids = ArrayList<Id>()
    private val idToIndex = HashMap<Id, Int>()

    private val quadTree = UltraFastQuadTree()

    private var frameCount = 0
    private val rebuildTreeEvery = 2

    // === SLEEP / COOLING STATE ===
    var alpha = 1f
        private set
    private var alphaTarget = 0f

    // Made baseline lower, dynamically scaled in syncData based on node count
    private var alphaDecay = 0.015f
    private val alphaMin = 0.001f
    private val reheatAlpha = 0.5f // Increased to give more energy for sorting dense clusters

    private var totalKineticEnergy = 0f
    private val sleepEnergyThreshold = 0.5f

    private var lastNodeCountSignature = 0

    override val isAsleep: Boolean get() = alpha < alphaMin && totalKineticEnergy < sleepEnergyThreshold

    /** Wake the simulation. Call when nodes added, dragged, or settings changed. */
    private fun reheat(intensity: Float = reheatAlpha) {
        alpha = max(alpha, intensity)
        alphaTarget = 0f
    }

    override suspend fun step(
        graphNodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        draggedNode: DragNodeData<Id>?
    ) = coroutineScope {
        // Detect structural change → reheat
        val structureSig = graphNodes.size * 31 + (connections.values.sumOf { it.size })
        if (structureSig != lastNodeCountSignature) {
            reheat(0.8f) // Heat up heavily on structural changes
            lastNodeCountSignature = structureSig
        }

        // Dragging always keeps us awake
        if (draggedNode != null) reheat(0.5f)

        // SLEEP GATE — fully skip work when at rest and no interaction
        if (isAsleep && draggedNode == null) {
            syncData(graphNodes, coordinates, velocities, connections)
            return@coroutineScope
        }

        syncData(graphNodes, coordinates, velocities, connections)
        frameCount++

        // 1. Build QuadTree
        if (frameCount % rebuildTreeEvery == 0 || frameCount == 1 || alpha > 0.3f) {
            quadTree.build(posX, posY, nodeCount)
        }

        val draggedIdx = draggedNode?.id?.let { idToIndex[it] } ?: -1
        val theta = if (nodeCount > 1000) 1.5f else 1.2f
        val thetaSq = theta * theta
        val softening = settings.circleSize * 0.5f

        // Scale forces by alpha
        val effectiveRepel = settings.repelForce * alpha
        val effectiveLink = settings.linkForce * alpha
        val effectiveCenter = settings.centerForce * alpha

        val cores = (avaliableCpuProcessors(settings.cpuCores) - 1).coerceAtLeast(1)
        val chunkSize = max(32, (nodeCount + cores - 1) / cores)
        val chunkCount = (nodeCount + chunkSize - 1) / chunkSize

        val chunkEnergy = FloatArray(chunkCount)

        val jobs = (0 until chunkCount).map { chunkIdx ->
            async(Dispatchers.Default) {
                val start = chunkIdx * chunkSize
                val end = min(start + chunkSize, nodeCount)
                val traversalStack = IntArray(256)
                val forceResult = FloatArray(2)

                // FORCE CALCULATION
                for (i in start until end) {
                    if (i == draggedIdx) continue

                    // A. N-Body Repulsion
                    quadTree.computeRepulsionIterative(
                        posX[i], posY[i], i,
                        effectiveRepel, softening, thetaSq,
                        traversalStack, forceResult
                    )
                    var fx = forceResult[0]
                    var fy = forceResult[1]

                    // B. Spring Links
                    val connStart = connectionsOffset[i]
                    val connEnd = connectionsOffset[i + 1]
                    val myDegree = degree[i]

                    for (c in connStart until connEnd) {
                        val otherIdx = connectionsFlat[c]
                        if (otherIdx == i || otherIdx >= nodeCount || otherIdx < 0) continue

                        val dx = posX[otherIdx] - posX[i]
                        val dy = posY[otherIdx] - posY[i]
                        val distSq = max(dx * dx + dy * dy, 0.01f)
                        val dist = sqrt(distSq)

                        val otherDegree = degree[otherIdx]
                        val bias = otherDegree.toFloat() / (myDegree + otherDegree).coerceAtLeast(1).toFloat()

                        // --- Integrates missing GraphViewSettings ---
                        val distanceMultiplier = if (dist > settings.linkDistance * 1.5f) {
                            settings.longDistanceLinkMultiplier
                        } else {
                            1f
                        }

                        // Compensate for QuadTree Repulsion on Connected Nodes
                        val repulsionCompensation = (effectiveRepel / max(distSq, softening)) *
                                (1f - settings.connectedRepulsionMultiplier)

                        val displacement = dist - settings.linkDistance
                        val linkMag = (effectiveLink * displacement * distanceMultiplier * bias) + repulsionCompensation

                        fx += (dx / dist) * linkMag
                        fy += (dy / dist) * linkMag
                    }

                    // C. Centering
                    val distFromCenter = sqrt(max(posX[i] * posX[i] + posY[i] * posY[i], 0.01f))
                    if (distFromCenter > 0.1f) {
                        fx -= posX[i] * effectiveCenter
                        fy -= posY[i] * effectiveCenter
                    }

                    // D. Force clamp prevents catastrophic jumps (Allows higher limits while hot)
                    val maxF = settings.maxForce * (1f + alpha)
                    val fMag = sqrt(fx * fx + fy * fy)
                    if (fMag > maxF) {
                        val s = maxF / fMag
                        fx *= s; fy *= s
                    }

                    forceX[i] = fx
                    forceY[i] = fy
                }

                // INTEGRATION
                val damping = settings.dampingFactor.let {
                    if (it == 0f) 0.9f else it.coerceAtMost(0.99f)
                }
                var localEnergy = 0f
                for (i in start until end) {
                    if (i == draggedIdx) continue
                    var vx = (velX[i] + forceX[i] * 0.1f) * damping
                    var vy = (velY[i] + forceY[i] * 0.1f) * damping

                    val vMagSq = vx * vx + vy * vy
                    if (vMagSq < 1e-3f) {
                        vx = 0f; vy = 0f
                    } else localEnergy += vMagSq

                    velX[i] = vx; velY[i] = vy
                    posX[i] += vx * 0.5f
                    posY[i] += vy * 0.5f
                }
                chunkEnergy[chunkIdx] = localEnergy
            }
        }
        jobs.awaitAll()

        // Handle dragged node
        if (draggedIdx >= 0 && draggedNode?.offset != null) {
            draggedNode.offset?.let { draggedNodeOffset ->
                posX[draggedIdx] = draggedNodeOffset.x
                posY[draggedIdx] = draggedNodeOffset.y
            }
            velX[draggedIdx] = 0f; velY[draggedIdx] = 0f
        }

        totalKineticEnergy = chunkEnergy.sum()
        alpha += (alphaTarget - alpha) * alphaDecay

        // Write back to the maps
        for (i in 0 until nodeCount) {
            val id = ids[i]
            coordinates[id] = Offset(posX[i], posY[i])
            velocities[id] = Offset(velX[i], velY[i])
        }
    }

    override fun reheat() {
        reheat(intensity = reheatAlpha)
    }

    private fun syncData(
        graphNodes: List<GraphNode<Id, Data>>,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        connections: Map<Id, List<Id>>
    ) {
        val n = graphNodes.size

        if (posX.size < n) {
            posX = FloatArray(n); posY = FloatArray(n)
            velX = FloatArray(n); velY = FloatArray(n)
            forceX = FloatArray(n); forceY = FloatArray(n)
        }
        if (connectionsOffset.size < n + 1) {
            connectionsOffset = IntArray(n + 1)
        }
        if (degree.size < n) {
            degree = IntArray(n)
        }

        ids.clear()
        idToIndex.clear()

        for (i in 0 until n) {
            val node = graphNodes[i]
            ids.add(node.id)
            idToIndex[node.id] = i

            var pos = coordinates[node.id] ?: Offset.Zero


            // Anti-Singularity Jitter: If node is exactly at 0,0, give it a tiny random scatter
            // This prevents massive explosions when multiple nodes spawn simultaneously
            if (pos == Offset.Zero) {
                pos = Offset(
                    (kotlin.random.Random.nextFloat() - 0.5f) * 10f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * 10f
                )
                coordinates[node.id] = pos
            }

            posX[i] = pos.x; posY[i] = pos.y
            val vel = velocities[node.id] ?: Offset.Zero
            velX[i] = vel.x; velY[i] = vel.y
        }

        var totalConns = 0
        for (i in 0 until n) {
            val conns = connections[graphNodes[i].id]
            if (conns != null) {
                for (cId in conns) {
                    if (idToIndex.containsKey(cId)) totalConns++
                }
            }
        }

        if (connectionsFlat.size < totalConns) {
            connectionsFlat = IntArray(totalConns.coerceAtLeast(16))
        }

        var write = 0
        for (i in 0 until n) {
            connectionsOffset[i] = write
            val conns = connections[graphNodes[i].id]
            var localDegree = 0
            if (conns != null) {
                for (cId in conns) {
                    val idx = idToIndex[cId]
                    if (idx != null && idx != i && idx < n) {
                        connectionsFlat[write++] = idx
                        localDegree++
                    }
                }
            }
            degree[i] = localDegree
        }
        connectionsOffset[n] = write

        nodeCount = n

        // Dynamic Alpha Scaling based on graph size
        // Large graphs cool slower to give them more time to untangle
        if (nodeCount > 0) {
            // e.g. 100 nodes = 0.02 decay. 1000 nodes = 0.005 decay.
            alphaDecay = 0.02f * (100f / nodeCount.coerceAtLeast(100).toFloat()).coerceAtLeast(0.002f)
        }
    }
}