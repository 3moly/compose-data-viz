package com.moly3.dataviz.core.graph.engine.impl.ultra

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.func.avaliableCpuProcessors
import com.moly3.dataviz.core.graph.hull.GroupSettings
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
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

    @Volatile
    private var freezingEnabled: Boolean = true

    override val isAsleep: Boolean get() {
        if (!freezingEnabled) return false
        return alpha <= 0f || (alpha < 0.02f && totalKineticEnergy < sleepEnergyThreshold)
    }

    private fun reheat(intensity: Float = reheatAlpha) {
        alpha = max(alpha, intensity)
        alphaTarget = 0f
    }

    fun setFreezingEnabled(enabled: Boolean) {
        if (freezingEnabled == enabled) return
        freezingEnabled = enabled
        if (!enabled) {
            // Kick the simulation back to life so the caller sees motion immediately.
            reheat(reheatAlpha)
        }
    }

    /** Returns whether freezing/sleep optimization is currently enabled. */
    fun isFreezingEnabled(): Boolean = freezingEnabled

    /**
     * Force the engine into its frozen state immediately, regardless of
     * current kinetic energy. Useful for pausing the layout for screenshots,
     * export, or when the view is off-screen.
     *
     * Has no effect if freezing is disabled via [setFreezingEnabled].
     */
    fun freeze() {
        if (!freezingEnabled) return
        alpha = 0f
        alphaTarget = 0f
        totalKineticEnergy = 0f
        // Zero out velocities so nothing drifts on the next non-frozen step.
        for (i in 0 until nodeCount) {
            velX[i] = 0f
            velY[i] = 0f
        }
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

        // [ADD] In step(), insert a new PHASE 1.5 between PHASE 1 (repulsion) and
// PHASE 2 (edges). Group cohesion + group-group separation are cheap, O(N+G²).
// ----------------------------------------------------------------------------

        // --- Phase 1.5: Group magnetization ------------------------------------------
        val gs = pendingGroupSettings
        if (gs.enabled && groupCount > 0 && (gs.cohesionForce > 0f || gs.groupSeparation > 0f)) {

            // 1. Compute centroids (single-thread; tiny work compared to repulsion)
            for (g in 0 until groupCount) {
                groupCentroidX[g] = 0f
                groupCentroidY[g] = 0f
                groupMemberCount[g] = 0
            }
            for (i in 0 until nodeCount) {
                val from = nodeGroupOffset[i]
                val to   = nodeGroupOffset[i + 1]
                val px = posX[i]; val py = posY[i]
                for (k in from until to) {
                    val gid = nodeGroupId[k]
                    groupCentroidX[gid] += px
                    groupCentroidY[gid] += py
                    groupMemberCount[gid]++
                }
            }
            for (g in 0 until groupCount) {
                val c = groupMemberCount[g]
                if (c > 0) {
                    val inv = 1f / c
                    groupCentroidX[g] *= inv
                    groupCentroidY[g] *= inv
                }
            }

            // 2. Inter-group separation: push centroids apart (apply to all members)
            // Precompute per-group nudge vectors so each member just adds them.
            val sepX = FloatArray(groupCount)
            val sepY = FloatArray(groupCount)
            val sepForce = gs.groupSeparation * alpha
            if (sepForce > 0f && groupCount > 1) {
                val soft = gs.groupSeparationSoftening
                val softSq = soft * soft
                for (a in 0 until groupCount) {
                    if (groupMemberCount[a] == 0) continue
                    for (b in a + 1 until groupCount) {
                        if (groupMemberCount[b] == 0) continue
                        var dx = groupCentroidX[a] - groupCentroidX[b]
                        var dy = groupCentroidY[a] - groupCentroidY[b]
                        var distSq = dx * dx + dy * dy
                        if (distSq < 0.01f) {
                            // Deterministic nudge so identical centroids separate
                            dx = ((a - b) and 0xF).toFloat() * 0.1f + 0.01f
                            dy = ((a + b) and 0xF).toFloat() * 0.1f + 0.01f
                            distSq = dx * dx + dy * dy
                        }
                        val mag = sepForce / max(distSq, softSq)
                        val nx = dx * mag
                        val ny = dy * mag
                        sepX[a] += nx; sepY[a] += ny
                        sepX[b] -= nx; sepY[b] -= ny
                    }
                }
            }

            // 3. Apply cohesion (toward own centroid) + separation (centroid-level) to forces
            val cohesion = gs.cohesionForce * alpha
            val drag = draggedIdx     // captured from above
            for (i in 0 until nodeCount) {
                if (i == drag) continue
                val from = nodeGroupOffset[i]
                val to   = nodeGroupOffset[i + 1]
                if (from == to) continue
                val px = posX[i]; val py = posY[i]
                var fx = 0f; var fy = 0f
                // Average cohesion vector across all groups this node belongs to.
                // (For single-group nodes this collapses to the simple case.)
                val memberships = to - from
                for (k in from until to) {
                    val gid = nodeGroupId[k]
                    fx += (groupCentroidX[gid] - px) * cohesion
                    fy += (groupCentroidY[gid] - py) * cohesion
                    fx += sepX[gid]
                    fy += sepY[gid]
                }
                val invM = 1f / memberships
                forceX[i] += fx * invM
                forceY[i] += fy * invM
            }
        }

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

//        totalKineticEnergy = chunkEnergy.sum()
//        alpha += (alphaTarget - alpha) * alphaDecay
//
//        // [NEW] Snap-Freeze: Crush the asymptotic tail to instantly kill micro-wobbles
//        if (alpha < 0.05f) {
//            alpha = 0f
//        }
        totalKineticEnergy = chunkEnergy.sum()
        alpha += (alphaTarget - alpha) * alphaDecay

        if (freezingEnabled) {
            // Snap-Freeze: Crush the asymptotic tail to instantly kill micro-wobbles
            if (alpha < 0.05f) {
                alpha = 0f
            }
        } else {
            // Keep a minimum "heat" so the physics never completely die
            // You can tweak this value. 0.05f keeps a gentle ambient movement.
            alpha = max(alpha, 0.05f)
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


        nodeCount = n

        // Invalidate hubScale cache on structural change
        if (structureChanged) {
            lastHubExponent = Float.NaN
        }

        // Dynamic alpha decay scaling
//        if (nodeCount > 0) {
//            alphaDecay = (0.05f * (100f / nodeCount.coerceAtLeast(100).toFloat()))
//                .coerceIn(0.02f, 0.08f)
//        }
        // Dynamic alpha decay scaling
        if (nodeCount > 0) {
            // Lowered the base multiplier from 0.05f to 0.015f for a longer, smoother layout time
            alphaDecay = (0.015f * (100f / nodeCount.coerceAtLeast(100).toFloat()))
                .coerceIn(0.005f, 0.03f)
        }

        // [MOVED] Group sync runs LAST so nodeCount is correct and posX/posY are
        // already populated. Pass `n` explicitly to be defensive.
        syncGroupsInternal(graphNodes, n)

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

    // ============================================================================
// PATCH: UltraFastEngine.kt — add group magnetization
// ============================================================================
// Add these fields, methods, and code blocks to your existing UltraFastEngine.
// I've marked every insertion point clearly with // [ADD] comments.
// ============================================================================

// ----------------------------------------------------------------------------
// [ADD] Imports at top of file (alongside existing ones)
// ----------------------------------------------------------------------------
// import com.moly3.dataviz.core.graph.model.GroupSettings


// ----------------------------------------------------------------------------
// [ADD] Inside class UltraFastEngine<Id, Data>, with the other SoA fields:
// ----------------------------------------------------------------------------

    // Group membership stored as flat CSR to support nodes-in-multiple-groups.
// nodeGroupOffset[i]..nodeGroupOffset[i+1] indexes into nodeGroupId for node i.
    private var nodeGroupOffset = IntArray(1)
    private var nodeGroupId    = IntArray(0)   // global integer group ids
    private var groupCount     = 0
    private var groupIdToString: Array<String> = emptyArray()   // index -> raw groupId string

    // Per-group running aggregates (rebuilt cheaply each step that uses cohesion)
    private var groupCentroidX = FloatArray(0)
    private var groupCentroidY = FloatArray(0)
    private var groupMemberCount = IntArray(0)

    // Last-seen signature so we know when to rebuild group SoA + reheat.
    private var lastGroupSignature = 0


// ----------------------------------------------------------------------------
// [ADD] New parameter to step() OR (cleaner) add a setter — pick whichever
// matches the rest of your engine's API. Below I show the setter approach,
// which avoids touching IGraphEngine.step's signature.
// ----------------------------------------------------------------------------

    // Latest group data handed in from Compose layer. Treated as immutable per-step.
    @Volatile private var pendingGroupResolver: ((Int) -> List<String>)? = null
    @Volatile
    private var pendingGroupSettings: GroupSettings = GroupSettings()

    /** Call from the Composable BEFORE step(). Cheap; just swaps references. */
    fun setGroupData(
        groupsForNodeIndex: ((Int) -> List<String>)?,   // index in `graphNodes` -> groupIds
        settings: GroupSettings
    ) {
        pendingGroupResolver = groupsForNodeIndex
        pendingGroupSettings = settings
    }


// ----------------------------------------------------------------------------
// [ADD] In syncData(...), AFTER the edge list is built and `nodeCount = n`,
// insert the group sync. (i.e. just before "if (structureChanged)" near the end.)
// ----------------------------------------------------------------------------

// --- Group SoA sync ---



// ----------------------------------------------------------------------------
// [ADD] New private method on the class:
// ----------------------------------------------------------------------------

    private fun syncGroupsInternal(graphNodes: List<GraphNode<Id, Data>>, n: Int) {
        val resolver = pendingGroupResolver

        if (resolver == null || !pendingGroupSettings.enabled || n == 0) {
            // Groups disabled or empty graph — make sure offset array still has
            // a valid sentinel so Phase 1.5's `nodeGroupOffset[i+1]` read is safe
            // even if someone re-enables groups mid-run.
            if (nodeGroupOffset.size < n + 1) {
                nodeGroupOffset = IntArray((n + 1).coerceAtLeast(1))
            }
            for (i in 0..n) nodeGroupOffset[i] = 0   // all empty ranges
            groupCount = 0
            return
        }

        // First pass: collect distinct group ids in stable insertion order,
        // count membership entries.
        val nameToId = HashMap<String, Int>()
        val names = ArrayList<String>()
        var totalEntries = 0
        val perNode = arrayOfNulls<List<String>>(n)

        for (i in 0 until n) {
            val list = resolver(i)
            perNode[i] = list
            for (gName in list) {
                if (gName !in nameToId) { // or !nameToId.containsKey(gName)
                    nameToId[gName] = names.size
                    names.add(gName)
                }
                totalEntries++
            }
        }

        val g = names.size

        // Signature: only reheat if it actually changed.
        var sig = g * 1_000_003 + totalEntries
        for (i in 0 until n) sig = sig * 31 + (perNode[i]?.size ?: 0)
        val groupsChanged = sig != lastGroupSignature
        lastGroupSignature = sig

        // [FIX] Grow nodeGroupOffset to (n+1), not n.  The previous version
        // checked `< n + 1` which is correct, but the initial field declaration
        // is `IntArray(1)`, so the very first call must grow it.  We just make
        // the growth more aggressive to amortize reallocation.
        if (nodeGroupOffset.size < n + 1) {
            nodeGroupOffset = IntArray((n + 1).coerceAtLeast(16))
        }
        if (nodeGroupId.size < totalEntries) {
            nodeGroupId = IntArray(totalEntries.coerceAtLeast(16))
        }
        if (groupCentroidX.size < g) {
            val cap = g.coerceAtLeast(8)
            groupCentroidX   = FloatArray(cap)
            groupCentroidY   = FloatArray(cap)
            groupMemberCount = IntArray(cap)
        }

        var w = 0
        for (i in 0 until n) {
            nodeGroupOffset[i] = w
            val list = perNode[i] ?: continue
            for (gName in list) {
                nodeGroupId[w++] = nameToId[gName]!!
            }
        }
        nodeGroupOffset[n] = w       // sentinel — required for `[i+1]` reads in Phase 1.5
        groupCount = g
        groupIdToString = Array(g) { names[it] }

        if (groupsChanged) reheat(0.4f)
    }

    fun unfreeze() {
        if (!freezingEnabled) return
        reheat(reheatAlpha)
    }
// ----------------------------------------------------------------------------

// --- end Phase 1.5 -----------------------------------------------------------


// ----------------------------------------------------------------------------
// [ADD] Public read-only snapshot accessors for the hull builder.
// These must be lock-free and stable enough for an off-thread reader:
// we return defensive copies of just what's needed.
// ----------------------------------------------------------------------------

    /** Snapshot of (groupId, [(x,y) for each member node]) for hull computation. */
    fun snapshotGroupsForHulls(): List<Pair<String, FloatArray>> {
        val g = groupCount
        if (g == 0 || nodeCount == 0) return emptyList()

        // First, count members per group from current CSR
        val counts = IntArray(g)
        for (i in 0 until nodeCount) {
            val from = nodeGroupOffset[i]
            val to   = nodeGroupOffset[i + 1]
            for (k in from until to) counts[nodeGroupId[k]]++
        }
        val pts = Array(g) { FloatArray(counts[it] * 2) }
        val wIdx = IntArray(g)
        for (i in 0 until nodeCount) {
            val from = nodeGroupOffset[i]
            val to   = nodeGroupOffset[i + 1]
            val px = posX[i]; val py = posY[i]
            for (k in from until to) {
                val gid = nodeGroupId[k]
                val w = wIdx[gid]
                pts[gid][w]     = px
                pts[gid][w + 1] = py
                wIdx[gid] = w + 2
            }
        }
        val out = ArrayList<Pair<String, FloatArray>>(g)
        for (gi in 0 until g) {
            if (counts[gi] > 0) out.add(groupIdToString[gi] to pts[gi])
        }
        return out
    }
}