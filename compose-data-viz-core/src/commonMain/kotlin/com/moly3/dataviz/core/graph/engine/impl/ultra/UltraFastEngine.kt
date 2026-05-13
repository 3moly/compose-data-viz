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
    private var edgeCount = 0

    // Node SoA
    private var posX = FloatArray(0)
    private var posY = FloatArray(0)
    private var velX = FloatArray(0)
    private var velY = FloatArray(0)
    private var forceX = FloatArray(0)
    private var forceY = FloatArray(0)
    private var degree = IntArray(0)

    // Per-node precomputed cache (rebuilt only when structure changes)
    private var hubScaleCache = FloatArray(0) // pow(degree, exponent) per node
    private var lastHubExponent = Float.NaN

    // Edge list (CSR is still kept for degree lookups, but main hot path uses edge list)
    // Each edge appears once (undirected). Pair (a, b) with a < b.
    private var edgeA = IntArray(0)
    private var edgeB = IntArray(0)

    // CSR for legacy use / connection traversal (kept for compatibility w/ rest of pipeline)
    private var connectionsOffset = IntArray(1)
    private var connectionsFlat = IntArray(0)

    // Per-thread force buffers (avoid atomic contention on edge force scatter)
    private var threadForceX: Array<FloatArray> = emptyArray()
    private var threadForceY: Array<FloatArray> = emptyArray()
    private var lastThreadCount = -1

    private val ids = ArrayList<Id>()
    private val idToIndex = HashMap<Id, Int>()

    private val quadTree = UltraFastQuadTree()

    private var frameCount = 0

    var alpha = 1f
        private set
    private var alphaTarget = 0f

    // INCREASE base decay to cool the simulation faster
    private var alphaDecay = 0.035f
    private val alphaMin = 0.001f
    private val reheatAlpha = 0.5f

    private var totalKineticEnergy = 0f

    // INCREASE threshold so it stops calculating micro-movements sooner
    private val sleepEnergyThreshold = 2.5f

//    private var totalKineticEnergy = 0f
//    private val sleepEnergyThreshold = 0.5f

    private var lastNodeCountSignature = 0

    override val isAsleep: Boolean get() {
        // Much simpler, more aggressive sleep detection.
        return alpha <= 0f || (alpha < 0.02f && totalKineticEnergy < sleepEnergyThreshold)
    }

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
        // Detect structural change → reheat + invalidate caches
        val structureSig = graphNodes.size * 31 + (connections.values.sumOf { it.size })
        val structureChanged = structureSig != lastNodeCountSignature
        if (structureChanged) {
            reheat(0.8f)
            lastNodeCountSignature = structureSig
        }

        if (draggedNode != null) reheat(0.01f)

        if (isAsleep && draggedNode == null) {
            syncData(graphNodes, coordinates, velocities, connections, structureChanged)
            return@coroutineScope
        }

        syncData(graphNodes, coordinates, velocities, connections, structureChanged)
        frameCount++


        // Refresh hubScaleCache if exponent setting changed
        if (settings.hubExpansionExponent != lastHubExponent) {
            recomputeHubScale(settings.hubExpansionExponent)
            lastHubExponent = settings.hubExpansionExponent
        }

        // Adaptive QuadTree rebuild: hot = every 2 frames, cool = every 4, very cool = every 8
        val rebuildEvery = when {
            alpha > 0.2f -> 1   // Always rebuild when hot
            alpha > 0.05f -> 2
            else -> 4           // Rebuild less often when cool
        }
        if (frameCount % rebuildEvery == 0 || frameCount == 1) {
            quadTree.build(posX, posY, nodeCount)
        }


        val draggedIdx = draggedNode?.id?.let { idToIndex[it] } ?: -1
        val theta = if (nodeCount > 1000) 1.5f else 1.2f
        val thetaSq = theta * theta
        val softening = settings.circleSize * 0.5f

        val effectiveRepel = settings.repelForce * alpha
        val effectiveLink = settings.linkForce * alpha
        val effectiveCenter = settings.centerForce * alpha

        val cores = (avaliableCpuProcessors(settings.cpuCores) - 1).coerceAtLeast(1)

        // Ensure per-thread force buffers exist for this thread count
        ensureThreadBuffers(cores, nodeCount)

        // Zero per-thread force buffers (KMP-safe; stdlib fill is intrinsified on JVM)
        for (t in 0 until cores) {
            threadForceX[t].fill(0f, 0, nodeCount)
            threadForceY[t].fill(0f, 0, nodeCount)
        }

        // Hoist damping outside hot loop
        val damping = settings.dampingFactor.let {
            if (it == 0f) 0.9f else it.coerceAtMost(0.99f)
        }

        // -------------------------------------------------------------
        // PHASE 1: NODE-PARALLEL repulsion (QuadTree Barnes-Hut)
        // Each chunk computes repulsion into its own slice of forceX/Y.
        // -------------------------------------------------------------
        val nodeChunkSize = max(32, (nodeCount + cores - 1) / cores)
        val nodeChunks = (nodeCount + nodeChunkSize - 1) / nodeChunkSize

        val repelJobs = (0 until nodeChunks).map { chunkIdx ->
            async(Dispatchers.Default) {
                val start = chunkIdx * nodeChunkSize
                val end = min(start + nodeChunkSize, nodeCount)
                val traversalStack = IntArray(256)
                val forceResult = FloatArray(2)

                for (i in start until end) {
                    if (i == draggedIdx) { forceX[i] = 0f; forceY[i] = 0f; continue }

                    // Repulsion via Barnes-Hut
                    quadTree.computeRepulsionIterative(
                        posX[i], posY[i], i,
                        effectiveRepel, softening, thetaSq,
                        traversalStack, forceResult
                    )
                    var fx = forceResult[0]
                    var fy = forceResult[1]

                    // Centering
                    val px = posX[i]; val py = posY[i]
                    val distFromCenterSq = px * px + py * py
                    if (distFromCenterSq > 0.01f) {
                        fx -= px * effectiveCenter
                        fy -= py * effectiveCenter
                    }

                    forceX[i] = fx
                    forceY[i] = fy
                }
            }
        }
        repelJobs.awaitAll()

        // -------------------------------------------------------------
        // PHASE 2: EDGE-PARALLEL spring forces.
        // Each edge processed ONCE (Newton's third law). Each worker
        // writes to its own per-thread buffer to avoid contention.
        // -------------------------------------------------------------
        if (edgeCount > 0) {
            val edgeChunkSize = max(64, (edgeCount + cores - 1) / cores)
            val edgeChunks = (edgeCount + edgeChunkSize - 1) / edgeChunkSize

            // Precompute per-edge invariants outside the loop
            val baseLinkDistance = settings.linkDistance
            val longMul = settings.longDistanceLinkMultiplier
            val connRepulsionMul = settings.connectedRepulsionMultiplier
            val invConnRepulsionMul = 1f - connRepulsionMul

            val linkJobs = (0 until edgeChunks).map { chunkIdx ->
                async(Dispatchers.Default) {
                    val start = chunkIdx * edgeChunkSize
                    val end = min(start + edgeChunkSize, edgeCount)
                    val tIdx = chunkIdx % cores
                    val tfx = threadForceX[tIdx]
                    val tfy = threadForceY[tIdx]

                    for (e in start until end) {
                        val a = edgeA[e]
                        val b = edgeB[e]
                        if (a == draggedIdx && b == draggedIdx) continue

                        var dx = posX[b] - posX[a]
                        var dy = posY[b] - posY[a]

                        // [NEW] Anti-Singularity: Force separation if exactly overlapped
                        if (dx == 0f && dy == 0f) {
                            dx = 0.01f + (a % 5) * 0.005f
                            dy = 0.01f + (b % 5) * 0.005f
                        }

                        val distSq = max(dx * dx + dy * dy, 0.01f)
                        // Fast inverse sqrt avoidance: we need dist for the linear spring
                        // (linear in displacement, not in 1/r). One sqrt is unavoidable
                        // unless we accept a less accurate model.
                        val dist = sqrt(distSq)
                        val invDist = 1f / dist

                        val degA = degree[a]
                        val degB = degree[b]
                        val degSum = (degA + degB).coerceAtLeast(1)
                        // bias for endpoint a (force ON a TOWARD b): degB / (degA+degB)
                        val biasA = degB.toFloat() / degSum.toFloat()
                        val biasB = degA.toFloat() / degSum.toFloat()

                        // Hub expansion: use cached pow values
                        val hubScale = (hubScaleCache[a] + hubScaleCache[b]) * 0.5f
//                        val effectiveLinkDistance = baseLinkDistance * hubScale.coerceIn(1f, 3f)
                        val degreeScale = if (degSum > 20) {
                            1f - (degSum - 20) * 0.02f  // Gradually reduce distance for very high degree nodes
                        } else {
                            1f
                        }.coerceAtLeast(0.5f)

                        val effectiveLinkDistance = baseLinkDistance * hubScale * degreeScale

                        val distMul = if (dist > effectiveLinkDistance * 1.5f) longMul else 1f

                        // Repulsion compensation
                        // FIXED - repulsion compensation should REDUCE attraction, not add to it
                        val repCompensation = (effectiveRepel / max(distSq, softening)) * connRepulsionMul

                        val displacement = dist - effectiveLinkDistance
                        val baseLinkMag = effectiveLink * displacement * distMul

// Apply to A (pulls toward B if displacement > 0)
                        val magA = baseLinkMag * biasA * (1f - connRepulsionMul)  // Reduce attraction based on repulsion
                        val fxA = dx * invDist * magA + dx * invDist * repCompensation  // Add repulsion separately
                        val fyA = dy * invDist * magA + dy * invDist * repCompensation

// Apply to B (Newton's third law: opposite direction for spring, but repulsion pushes apart)
                        val magB = baseLinkMag * biasB * (1f - connRepulsionMul)
                        val fxB = -dx * invDist * magB - dx * invDist * repCompensation  // Repulsion pushes apart
                        val fyB = -dy * invDist * magB - dy * invDist * repCompensation

                        val antiStickDist = settings.circleSize * 2f  // 2x circle diameter
                        val antiStickForce = effectiveRepel * 10f  // 10x normal repulsion for very close nodes

                        if (dist < antiStickDist) {
                            val stickFactor = (1f - dist / antiStickDist) * (1f - dist / antiStickDist)  // Quadratic falloff
                            val antiFx = dx * invDist * antiStickForce * stickFactor
                            val antiFy = dy * invDist * antiStickForce * stickFactor

                            tfx[a] -= antiFx; tfy[a] -= antiFy
                            tfx[b] += antiFx; tfy[b] += antiFy
                        }
                        if (a != draggedIdx) { tfx[a] += fxA; tfy[a] += fyA }
                        if (b != draggedIdx) { tfx[b] += fxB; tfy[b] += fyB }
                    }
                }
            }
            linkJobs.awaitAll()

            // Reduce per-thread buffers into forceX/Y (node-parallel)
            val reduceJobs = (0 until nodeChunks).map { chunkIdx ->
                async(Dispatchers.Default) {
                    val start = chunkIdx * nodeChunkSize
                    val end = min(start + nodeChunkSize, nodeCount)
                    for (i in start until end) {
                        if (i == draggedIdx) continue
                        var fx = forceX[i]
                        var fy = forceY[i]
                        for (t in 0 until cores) {
                            fx += threadForceX[t][i]
                            fy += threadForceY[t][i]
                        }
                        forceX[i] = fx
                        forceY[i] = fy
                    }
                }
            }
            reduceJobs.awaitAll()
        }

        // -------------------------------------------------------------
        // PHASE 3: Force clamp + integrate (node-parallel)
        // -------------------------------------------------------------
        val maxF = settings.maxForce * (1f + alpha)
        val maxFSq = maxF * maxF

        val chunkEnergy = FloatArray(nodeChunks)
        val adaptiveTimestep = when {
            alpha > 0.5f -> 0.15f   // Faster movement when hot
            alpha > 0.2f -> 0.12f
            alpha > 0.1f -> 0.1f
            else -> 0.08f           // More stable when cool
        }

        val integrateJobs = (0 until nodeChunks).map { chunkIdx ->
            async(Dispatchers.Default) {
                val start = chunkIdx * nodeChunkSize
                val end = min(start + nodeChunkSize, nodeCount)
                var localEnergy = 0f

                for (i in start until end) {
                    if (i == draggedIdx) continue
                    var fx = forceX[i]
                    var fy = forceY[i]

                    // Clamp force
                    val fMagSq = fx * fx + fy * fy
                    if (fMagSq > maxFSq) {
                        val s = maxF / sqrt(fMagSq)
                        fx *= s; fy *= s
                    }

                    // Use adaptive timestep for velocity integration
                    var vx = (velX[i] + fx * adaptiveTimestep) * damping
                    var vy = (velY[i] + fy * adaptiveTimestep) * damping

                    val vMagSq = vx * vx + vy * vy
                    if (vMagSq < 1e-3f) {
                        vx = 0f; vy = 0f
                    } else {
                        localEnergy += vMagSq
                    }

                    velX[i] = vx; velY[i] = vy

                    // Apply velocity smoothing for stability
                    posX[i] += vx * (0.4f + alpha * 0.3f)  // Scale position update with alpha
                    posY[i] += vy * (0.4f + alpha * 0.3f)
                }
                chunkEnergy[chunkIdx] = localEnergy
            }
        }
        integrateJobs.awaitAll()

        // Handle dragged node
        if (draggedIdx >= 0 && draggedNode?.offset != null) {
            draggedNode.offset.let { o ->
                posX[draggedIdx] = o.x
                posY[draggedIdx] = o.y
            }
            velX[draggedIdx] = 0f; velY[draggedIdx] = 0f
        }

        totalKineticEnergy = chunkEnergy.sum()
        alpha += (alphaTarget - alpha) * alphaDecay

        // [NEW] Snap-Freeze: Crush the asymptotic tail to instantly kill micro-wobbles
        if (alpha < 0.05f) {
            alpha = 0f
        }

        // Write back to maps
        for (i in 0 until nodeCount) {
            val id = ids[i]
            coordinates[id] = Offset(posX[i], posY[i])
            velocities[id] = Offset(velX[i], velY[i])
        }
    }

    override fun reheat() {
        reheat(intensity = reheatAlpha)
    }

    private fun ensureThreadBuffers(cores: Int, n: Int) {
        if (lastThreadCount != cores || threadForceX.isEmpty() || threadForceX[0].size < n) {
            threadForceX = Array(cores) { FloatArray(n.coerceAtLeast(16)) }
            threadForceY = Array(cores) { FloatArray(n.coerceAtLeast(16)) }
            lastThreadCount = cores
        }
    }

    private fun recomputeHubScale(exponent: Float) {
        if (hubScaleCache.size < nodeCount) {
            hubScaleCache = FloatArray(nodeCount.coerceAtLeast(16))
        }
        for (i in 0 until nodeCount) {
            val d = degree[i].toFloat()
            // Cap hub expansion to prevent orbits from becoming too large
            hubScaleCache[i] = min(d.pow(exponent), 4f)  // Cap at 4x base distance
        }
    }

    private fun syncData(
        graphNodes: List<GraphNode<Id, Data>>,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        connections: Map<Id, List<Id>>,
        structureChanged: Boolean
    ) {
        val n = graphNodes.size

        if (posX.size < n) {
            val newCap = n.coerceAtLeast(16)
            posX = FloatArray(newCap); posY = FloatArray(newCap)
            velX = FloatArray(newCap); velY = FloatArray(newCap)
            forceX = FloatArray(newCap); forceY = FloatArray(newCap)
        }
        if (connectionsOffset.size < n + 1) {
            connectionsOffset = IntArray(n + 1)
        }
        if (degree.size < n) {
            degree = IntArray(n.coerceAtLeast(16))
        }

        ids.clear()
        idToIndex.clear()

        for (i in 0 until n) {
            val node = graphNodes[i]
            ids.add(node.id)
            idToIndex[node.id] = i

            var pos = coordinates[node.id] ?: Offset.Zero
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

        // Count edges (each undirected edge counted once: only a < b)
        // First pass: count total directed entries (for CSR sizing)
        var totalCsrConns = 0
        for (i in 0 until n) {
            val conns = connections[graphNodes[i].id]
            if (conns != null) {
                for (cId in conns) {
                    if (idToIndex.containsKey(cId)) totalCsrConns++
                }
            }
        }

        if (connectionsFlat.size < totalCsrConns) {
            connectionsFlat = IntArray(totalCsrConns.coerceAtLeast(16))
        }

        // Build CSR + degree
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

        // Build deduplicated edge list (each undirected edge once: a < b)
        // Upper bound = total CSR entries / 2 + slack
        val edgeCap = (totalCsrConns / 2 + 16).coerceAtLeast(16)
        if (edgeA.size < edgeCap) {
            edgeA = IntArray(edgeCap)
            edgeB = IntArray(edgeCap)
        }
        var eWrite = 0
        for (i in 0 until n) {
            val from = connectionsOffset[i]
            val to = connectionsOffset[i + 1]
            for (k in from until to) {
                val j = connectionsFlat[k]
                if (j > i) { // dedupe undirected
                    if (eWrite >= edgeA.size) {
                        // very rare: grow
                        edgeA = edgeA.copyOf(edgeA.size * 2)
                        edgeB = edgeB.copyOf(edgeB.size * 2)
                    }
                    edgeA[eWrite] = i
                    edgeB[eWrite] = j
                    eWrite++
                }
            }
        }
        edgeCount = eWrite

        nodeCount = n

        // Invalidate hubScale cache on structural change
        if (structureChanged) {
            lastHubExponent = Float.NaN
        }

        // Dynamic alpha decay scaling
        if (nodeCount > 0) {
            // Correctly apply coerceIn to the FINAL multiplication result
            alphaDecay = (0.05f * (100f / nodeCount.coerceAtLeast(100).toFloat()))
                .coerceIn(0.02f, 0.08f)
        }
    }
}