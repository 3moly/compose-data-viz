package com.moly3.dataviz.core.graph.engine.impl.ultra

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.func.avaliableCpuProcessors
import com.moly3.dataviz.core.graph.hull.GroupSettings
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.dataviz.core.graph.model.GroupId
import com.moly3.dataviz.core.graph.model.GroupIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance force-directed graph engine.
 *
 * Behavior is tuned via [config]; physics via the [GraphViewSettings] passed
 * to [step]. Pass a custom [UltraFastEngineConfig] for non-default heat /
 * decay / sleep behavior.
 *
 * Anti-stuck design: ALL forces (repulsion, centering, springs, groups) decay
 * together with [alpha] — the layout stays balanced and never drifts. The only
 * stuck-overlap problem is that a slept graph runs no physics at all, so
 * coincident nodes can never separate. The fix is purely a sleep gate: the
 * graph is not allowed to fall asleep while any two nodes still overlap. While
 * awake, the normal balanced physics pushes them apart; once clean, it sleeps.
 */
class UltraFastEngine<Id, Data>(
    var config: UltraFastEngineConfig = UltraFastEngineConfig.Default
) : IGraphEngine<Id, Data> {

    // -----------------------------------------------------------------
    // SoA storage
    // -----------------------------------------------------------------
    // -----------------------------------------------------------------
    // Group data — CSR-style, keyed by node INDEX (built from GroupIndex).
    // nodeGroupId[k] / nodeGroupWeight[k] are parallel; nodeGroupOffset
    // partitions them per node.
    // -----------------------------------------------------------------
    private var nodeGroupOffset = IntArray(1)
    private var nodeGroupId = IntArray(0)
    private var nodeGroupWeight = FloatArray(0)
    private var groupCount = 0
    private var groupIdToValue: Array<GroupId> = emptyArray()

    private var groupCentroidX = FloatArray(0)
    private var groupCentroidY = FloatArray(0)
    private var groupMemberCount = IntArray(0)
    // Sum of weights per group — centroid is weight-averaged.
    private var groupWeightSum = FloatArray(0)

    /** -1 means "no signature yet" so the very first sync doesn't fire a fake reheat. */
    private var lastGroupSignature = -1

    @Volatile
    private var pendingGroupIndex: GroupIndex<Id>? = null

    @Volatile
    private var pendingGroupSettings: GroupSettings = GroupSettings()

    override fun setGroupData(
        groupIndex: GroupIndex<Id>?,
        settings: GroupSettings,
    ) {
        pendingGroupIndex = groupIndex
        pendingGroupSettings = settings
    }

    private var nodeCount = 0
    private var edgeCount = 0

    private var posX = FloatArray(0)
    private var posY = FloatArray(0)
    private var velX = FloatArray(0)
    private var velY = FloatArray(0)
    private var forceX = FloatArray(0)
    private var forceY = FloatArray(0)
    private var degree = IntArray(0)

    private var hubScaleCache = FloatArray(0)
    private var lastHubExponent = Float.NaN

    // Edge list (each undirected edge once, a < b)
    private var edgeA = IntArray(0)
    private var edgeB = IntArray(0)

    // CSR for legacy / traversal
    private var connectionsOffset = IntArray(1)
    private var connectionsFlat = IntArray(0)

    // Per-thread force scatter buffers
    private var threadForceX: Array<FloatArray> = emptyArray()
    private var threadForceY: Array<FloatArray> = emptyArray()
    private var lastThreadCount = -1

    private val ids = ArrayList<Id>()
    private val idToIndex = HashMap<Id, Int>()

    private val quadTree = UltraFastQuadTree()

    private var frameCount = 0

    // -----------------------------------------------------------------
    // Heat state
    // -----------------------------------------------------------------
    var alpha = config.startAlpha
        private set
    private var alphaTarget = 0f
    private var alphaDecay = config.baseAlphaDecay

    private var totalKineticEnergy = 0f
    private var lastNodeCountSignature = 0

    /**
     * True when at least one pair of nodes is closer than the overlap radius.
     * Computed cheaply each frame via a uniform grid. Used ONLY as a sleep
     * gate — it never produces a force, so it cannot fight the layout.
     */
    private var hasOverlap = false

    @Volatile
    private var freezingEnabled: Boolean = true

    override val isAsleep: Boolean
        get() {
            if (!freezingEnabled) return false
            // Never report asleep while nodes are still stacked — otherwise
            // step() early-exits and they can never separate.
            if (hasOverlap) return false
            return alpha <= 0f ||
                    (alpha < config.asleepAlphaCheck && totalKineticEnergy < config.sleepEnergyThreshold)
        }

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    override fun reheat() {
        reheatInternal(config.reheatAlpha)
    }

    /** Soft wake — for settings changes, single-node adds, post-drag settle. */
    override fun nudge() {
        reheatInternal(config.nudgeAlpha)
    }

    private fun reheatInternal(intensity: Float) {
        alpha = max(alpha, intensity)
        alphaTarget = 0f
    }

    // -----------------------------------------------------------------
    // step()
    // -----------------------------------------------------------------

    override suspend fun step(
        graphNodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        draggedNode: DragNodeData<Id>?,
    ) = coroutineScope {
        // ---- Structural change detection ----
        val structureSig = graphNodes.size * 31 + connections.values.sumOf { it.size }
        val structureChanged = structureSig != lastNodeCountSignature
        if (structureChanged) {
            val prevNodeCount = nodeCount
            val newNodeCount = graphNodes.size
            val delta = abs(newNodeCount - prevNodeCount)
            val bigThreshold = (newNodeCount * config.bigChangeFraction).toInt().coerceAtLeast(1)

            when {
                prevNodeCount == 0 -> {
                    // first load — no reheat needed
                }
                delta <= config.gentleAddThreshold -> {
                    nudge()
                }
                delta <= bigThreshold -> reheatInternal(config.moderateChangeAlpha)
                else -> reheatInternal(config.reheatAlpha)
            }
            lastNodeCountSignature = structureSig
        }

        if (draggedNode != null) reheatInternal(config.dragReheatAlpha)

        // Early exit if truly idle AND nothing is overlapping AND nothing changed.
        // (isAsleep already returns false while hasOverlap is set, so a stacked
        //  graph keeps stepping until it has un-stacked itself.)
        if (isAsleep && draggedNode == null && !structureChanged) {
            syncData(graphNodes, coordinates, velocities, connections, false)
            return@coroutineScope
        }

        syncData(graphNodes, coordinates, velocities, connections, structureChanged)
        frameCount++

        if (settings.hubExpansionExponent != lastHubExponent) {
            recomputeHubScale(settings.hubExpansionExponent)
            lastHubExponent = settings.hubExpansionExponent
        }

        // Adaptive quadtree rebuild
        val rebuildEvery = when {
            alpha > 0.2f -> 1
            alpha > 0.05f -> 2
            else -> 4
        }
        if (frameCount % rebuildEvery == 0 || frameCount == 1) {
            quadTree.build(posX, posY, nodeCount)
        }

        val draggedIdx = draggedNode?.id?.let { idToIndex[it] } ?: -1
        val theta = if (nodeCount > config.bigGraphNodeCount) config.thetaLargeGraph else config.thetaSmallGraph
        val thetaSq = theta * theta
        val softening = settings.circleSize * 0.5f

        // ALL forces decay together with alpha. This symmetry is what keeps the
        // layout balanced — repulsion (push apart) and centering+springs (pull
        // together) must scale by the same factor or the graph drifts.
        val effectiveRepel = settings.repelForce * alpha
        val effectiveLink = settings.linkForce * alpha
        val effectiveCenter = settings.centerForce * alpha

        val cores = (avaliableCpuProcessors(settings.cpuCores) - 1).coerceAtLeast(1)
        ensureThreadBuffers(cores, nodeCount)
        for (t in 0 until cores) {
            threadForceX[t].fill(0f, 0, nodeCount)
            threadForceY[t].fill(0f, 0, nodeCount)
        }

        val damping = settings.dampingFactor.let {
            if (it == 0f) config.fallbackDamping else it.coerceAtMost(config.maxDamping)
        }

        // ---- PHASE 1: Repulsion (Barnes-Hut) ----
        val nodeChunkSize = max(32, (nodeCount + cores - 1) / cores)
        val nodeChunks = (nodeCount + nodeChunkSize - 1) / nodeChunkSize

        val repelJobs = (0 until nodeChunks).map { chunkIdx ->
            async(Dispatchers.Default) {
                val start = chunkIdx * nodeChunkSize
                val end = min(start + nodeChunkSize, nodeCount)
                val traversalStack = IntArray(256)
                val forceResult = FloatArray(2)

                for (i in start until end) {
                    if (i == draggedIdx) {
                        forceX[i] = 0f; forceY[i] = 0f; continue
                    }
                    quadTree.computeRepulsionIterative(
                        posX[i], posY[i], i,
                        effectiveRepel, softening, thetaSq,
                        traversalStack, forceResult
                    )
                    var fx = forceResult[0]
                    var fy = forceResult[1]

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

        // ---- PHASE 1.5: Group magnetization ----
        val gs = pendingGroupSettings
        if (gs.enabled && groupCount > 0 && (gs.cohesionForce > 0f || gs.groupSeparation > 0f)) {
            applyGroupForces(gs, draggedIdx)
        }

        // ---- PHASE 2: Edge spring forces ----
        if (edgeCount > 0) {
            applyEdgeForces(
                cores, settings, effectiveLink, effectiveRepel, softening, draggedIdx
            )

            // Reduce per-thread buffers
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

        // ---- PHASE 3: Clamp + integrate ----
        val maxF = settings.maxForce * (1f + alpha)
        val maxFSq = maxF * maxF
        val chunkEnergy = FloatArray(nodeChunks)
        val adaptiveTimestep = when {
            alpha > 0.5f -> config.timestepHot
            alpha > 0.2f -> config.timestepWarm
            alpha > 0.1f -> config.timestepCool
            else -> config.timestepCold
        }
        val posScale = config.posUpdateBlend + alpha * config.posUpdateAlphaScale

        val integrateJobs = (0 until nodeChunks).map { chunkIdx ->
            async(Dispatchers.Default) {
                val start = chunkIdx * nodeChunkSize
                val end = min(start + nodeChunkSize, nodeCount)
                var localEnergy = 0f

                for (i in start until end) {
                    if (i == draggedIdx) continue
                    var fx = forceX[i]
                    var fy = forceY[i]

                    val fMagSq = fx * fx + fy * fy
                    if (fMagSq > maxFSq) {
                        val s = maxF / sqrt(fMagSq)
                        fx *= s; fy *= s
                    }

                    var vx = (velX[i] + fx * adaptiveTimestep) * damping
                    var vy = (velY[i] + fy * adaptiveTimestep) * damping

                    val vMagSq = vx * vx + vy * vy
                    if (vMagSq < 1e-5f) {
                        vx = 0f; vy = 0f
                    } else {
                        localEnergy += vMagSq
                    }

                    velX[i] = vx; velY[i] = vy
                    posX[i] += vx * posScale
                    posY[i] += vy * posScale
                }
                chunkEnergy[chunkIdx] = localEnergy
            }
        }
        integrateJobs.awaitAll()

        // Dragged node override
        if (draggedIdx >= 0 && draggedNode?.offset != null) {
            posX[draggedIdx] = draggedNode.offset.x
            posY[draggedIdx] = draggedNode.offset.y
            velX[draggedIdx] = 0f
            velY[draggedIdx] = 0f
        }

        // ---- Overlap detection (sleep gate only — produces no force) ----
        hasOverlap = detectOverlap(settings)

        // Heat decay + snap-freeze
        totalKineticEnergy = chunkEnergy.sum()
        alpha += (alphaTarget - alpha) * alphaDecay

        if (freezingEnabled) {
            if (hasOverlap) {
                // Hold a small heat floor so cold-but-stacked nodes still move.
                // The normal balanced physics (which is still running because
                // alpha > 0) separates them; no special force is needed.
                alpha = max(alpha, config.overlapResolveAlpha)
            } else if (alpha < config.sleepAlphaThreshold &&
                totalKineticEnergy < config.sleepEnergyThreshold
            ) {
                alpha = 0f
            }
        } else {
            alpha = max(alpha, config.minAlphaWhenUnfrozen)
        }

        // Write back
        for (i in 0 until nodeCount) {
            val id = ids[i]
            coordinates[id] = Offset(posX[i], posY[i])
            velocities[id] = Offset(velX[i], velY[i])
        }
    }

    // -----------------------------------------------------------------
    // Overlap detection — cheap uniform grid, single pass, no force output.
    // Returns true as soon as ANY pair is closer than the overlap radius.
    // -----------------------------------------------------------------

    private fun detectOverlap(settings: GraphViewSettings): Boolean {
        if (nodeCount < 2) return false

        val minDist = settings.circleSize * config.overlapDistanceMultiplier
        if (minDist <= 0f) return false
        val minDistSq = minDist * minDist

        // Bounding box
        var minX = posX[0]; var maxX = posX[0]
        var minY = posY[0]; var maxY = posY[0]
        for (i in 1 until nodeCount) {
            val x = posX[i]; val y = posY[i]
            if (x < minX) minX = x else if (x > maxX) maxX = x
            if (y < minY) minY = y else if (y > maxY) maxY = y
        }

        val cell = max(minDist, 1f)
        val cols = (((maxX - minX) / cell).toInt() + 1).coerceAtLeast(1)
        val rows = (((maxY - minY) / cell).toInt() + 1).coerceAtLeast(1)

        // Spread-out graphs: a huge grid is pointless and overlaps are rare.
        // Fall back to a capped O(n^2) scan that bails on the first hit.
        if (cols.toLong() * rows.toLong() > 4_000_000L) {
            if (nodeCount > 4000) return false
            for (i in 0 until nodeCount) {
                val px = posX[i]; val py = posY[i]
                for (j in i + 1 until nodeCount) {
                    val dx = px - posX[j]
                    val dy = py - posY[j]
                    if (dx * dx + dy * dy < minDistSq) return true
                }
            }
            return false
        }

        val cellHead = IntArray(cols * rows) { -1 }
        val nextInCell = IntArray(nodeCount)
        for (i in 0 until nodeCount) {
            val cx = (((posX[i] - minX) / cell).toInt()).coerceIn(0, cols - 1)
            val cy = (((posY[i] - minY) / cell).toInt()).coerceIn(0, rows - 1)
            val c = cy * cols + cx
            nextInCell[i] = cellHead[c]
            cellHead[c] = i
        }

        for (i in 0 until nodeCount) {
            val cx = (((posX[i] - minX) / cell).toInt()).coerceIn(0, cols - 1)
            val cy = (((posY[i] - minY) / cell).toInt()).coerceIn(0, rows - 1)
            val px = posX[i]; val py = posY[i]
            for (gy in (cy - 1)..(cy + 1)) {
                if (gy < 0 || gy >= rows) continue
                for (gx in (cx - 1)..(cx + 1)) {
                    if (gx < 0 || gx >= cols) continue
                    var j = cellHead[gy * cols + gx]
                    while (j != -1) {
                        if (j > i) {
                            val dx = px - posX[j]
                            val dy = py - posY[j]
                            if (dx * dx + dy * dy < minDistSq) return true
                        }
                        j = nextInCell[j]
                    }
                }
            }
        }
        return false
    }

    // -----------------------------------------------------------------
    // Extracted force phases
    // -----------------------------------------------------------------

    private fun applyGroupForces(gs: GroupSettings, draggedIdx: Int) {
        for (g in 0 until groupCount) {
            groupCentroidX[g] = 0f
            groupCentroidY[g] = 0f
            groupMemberCount[g] = 0
            groupWeightSum[g] = 0f
        }
        for (i in 0 until nodeCount) {
            val from = nodeGroupOffset[i]
            val to = nodeGroupOffset[i + 1]
            val px = posX[i]; val py = posY[i]
            for (k in from until to) {
                val gid = nodeGroupId[k]
                val wgt = nodeGroupWeight[k]
                groupCentroidX[gid] += px * wgt
                groupCentroidY[gid] += py * wgt
                groupWeightSum[gid] += wgt
                groupMemberCount[gid]++
            }
        }
        for (g in 0 until groupCount) {
            val ws = groupWeightSum[g]
            if (ws > 1e-4f) {
                val inv = 1f / ws
                groupCentroidX[g] *= inv
                groupCentroidY[g] *= inv
            }
        }

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

        val cohesion = gs.cohesionForce * alpha
        for (i in 0 until nodeCount) {
            if (i == draggedIdx) continue
            val from = nodeGroupOffset[i]
            val to = nodeGroupOffset[i + 1]
            if (from == to) continue
            val px = posX[i]; val py = posY[i]
            var fx = 0f; var fy = 0f
            val memberships = to - from
            for (k in from until to) {
                val gid = nodeGroupId[k]
                val wgt = nodeGroupWeight[k]
                // Cohesion pull scales by this node's membership weight:
                // a loose member (weight < 1) is pulled in more gently.
                fx += (groupCentroidX[gid] - px) * cohesion * wgt
                fy += (groupCentroidY[gid] - py) * cohesion * wgt
                // Separation is a group-level push; weight does not apply.
                fx += sepX[gid]
                fy += sepY[gid]
            }
            val invM = 1f / memberships
            forceX[i] += fx * invM
            forceY[i] += fy * invM
        }
    }

    private suspend fun applyEdgeForces(
        cores: Int,
        settings: GraphViewSettings,
        effectiveLink: Float,
        effectiveRepel: Float,
        softening: Float,
        draggedIdx: Int,
    ) = coroutineScope {
        val edgeChunkSize = max(64, (edgeCount + cores - 1) / cores)
        val edgeChunks = (edgeCount + edgeChunkSize - 1) / edgeChunkSize

        val baseLinkDistance = settings.linkDistance
        val longMul = settings.longDistanceLinkMultiplier
        val connRepulsionMul = settings.connectedRepulsionMultiplier
        val antiStickDist = settings.circleSize * config.antiStickDistanceMultiplier
        val antiStickForce = effectiveRepel * config.antiStickForceMultiplier

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
                    if (dx == 0f && dy == 0f) {
                        dx = 0.01f + (a % 5) * 0.005f
                        dy = 0.01f + (b % 5) * 0.005f
                    }

                    val distSq = max(dx * dx + dy * dy, 0.01f)
                    val dist = sqrt(distSq)
                    val invDist = 1f / dist

                    val degA = degree[a]
                    val degB = degree[b]
                    val degSum = (degA + degB).coerceAtLeast(1)
                    val biasA = degB.toFloat() / degSum.toFloat()
                    val biasB = degA.toFloat() / degSum.toFloat()

                    val hubScale = (hubScaleCache[a] + hubScaleCache[b]) * 0.5f
                    val degreeScale = if (degSum > 20) {
                        (1f - (degSum - 20) * 0.02f).coerceAtLeast(0.5f)
                    } else 1f

                    val effectiveLinkDistance = baseLinkDistance * hubScale * degreeScale
                    val distMul = if (dist > effectiveLinkDistance * 1.5f) longMul else 1f
                    val repCompensation = (effectiveRepel / max(distSq, softening)) * connRepulsionMul

                    val displacement = dist - effectiveLinkDistance
                    val baseLinkMag = effectiveLink * displacement * distMul

                    val magA = baseLinkMag * biasA * (1f - connRepulsionMul)
                    val fxA = dx * invDist * magA + dx * invDist * repCompensation
                    val fyA = dy * invDist * magA + dy * invDist * repCompensation

                    val magB = baseLinkMag * biasB * (1f - connRepulsionMul)
                    val fxB = -dx * invDist * magB - dx * invDist * repCompensation
                    val fyB = -dy * invDist * magB - dy * invDist * repCompensation

                    if (dist < antiStickDist) {
                        val t = 1f - dist / antiStickDist
                        val stickFactor = t * t
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
    }

    // -----------------------------------------------------------------
    // syncData / setup
    // -----------------------------------------------------------------

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
            hubScaleCache[i] = min(d.pow(exponent), config.maxHubScale)
        }
    }

    private fun syncData(
        graphNodes: List<GraphNode<Id, Data>>,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        connections: Map<Id, List<Id>>,
        structureChanged: Boolean,
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

        if (structureChanged) {
            lastHubExponent = Float.NaN
        }

        // "more nodes → slower decay" — symmetric around N=300
        alphaDecay = if (config.adaptiveDecayByNodeCount && nodeCount > 0) {
            (config.baseAlphaDecay * (300f / nodeCount.coerceAtLeast(1)))
                .coerceIn(config.minAlphaDecay, config.maxAlphaDecay)
        } else {
            config.baseAlphaDecay
        }

        syncGroupsInternal(graphNodes, n)

        for (i in 0 until n) {
            val node = graphNodes[i]
            ids.add(node.id)
            idToIndex[node.id] = i

            var pos = coordinates[node.id] ?: Offset.Zero
            if (pos == Offset.Zero) {
                // Nodes with no position get a one-time random spawn so they
                // don't all start coincident. Existing positions pass through
                // untouched — never any per-frame jitter.
                pos = Offset(
                    (kotlin.random.Random.nextFloat() - 0.5f) * config.spawnSpread,
                    (kotlin.random.Random.nextFloat() - 0.5f) * config.spawnSpread
                )
                coordinates[node.id] = pos
            }
            posX[i] = pos.x; posY[i] = pos.y
            val vel = velocities[node.id] ?: Offset.Zero
            velX[i] = vel.x; velY[i] = vel.y
        }

        // De-stack: nodes that loaded at the SAME position get a tiny one-time
        // deterministic offset so Barnes-Hut has a real direction to work with.
        // Runs only on a structural change — never per idle frame.
        if (structureChanged && n > 1) {
            deStackCoincidentNodes(coordinates)
        }

        // Count CSR entries
        var totalCsrConns = 0
        for (i in 0 until n) {
            val conns = connections[graphNodes[i].id] ?: continue
            for (cId in conns) {
                if (idToIndex.containsKey(cId)) totalCsrConns++
            }
        }
        if (connectionsFlat.size < totalCsrConns) {
            connectionsFlat = IntArray(totalCsrConns.coerceAtLeast(16))
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

        // Deduplicated edge list (a < b)
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
                if (j > i) {
                    if (eWrite >= edgeA.size) {
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
    }

    /**
     * One-time fix for nodes that arrive stacked on an identical position.
     * Quantizes each position into a small map; any collision gets a tiny
     * golden-angle spiral offset. Cheap (O(n)) and runs only on structural
     * changes. Offsets are intentionally small so the layout still looks
     * "loaded at the same place" — the physics then spreads them properly.
     */
    private fun deStackCoincidentNodes(coordinates: MutableMap<Id, Offset>) {
        val seen = HashMap<Long, Int>()
        for (i in 0 until nodeCount) {
            // Quantize to ~0.5 units so near-identical positions collide too.
            val qx = (posX[i] * 2f).toInt()
            val qy = (posY[i] * 2f).toInt()
            val key = (qx.toLong() shl 32) xor (qy.toLong() and 0xFFFFFFFFL)
            val prior = seen[key]
            if (prior == null) {
                seen[key] = i
            } else {
                // Spiral each duplicate outward by a stable golden-angle step.
                val rank = seen.size + i
                val ang = rank * 2.3999632f
                val r = config.deStackRadius * (1f + (rank and 0x7) * 0.15f)
                posX[i] += cos(ang) * r
                posY[i] += sin(ang) * r
                coordinates[ids[i]] = Offset(posX[i], posY[i])
            }
        }
    }

    // -----------------------------------------------------------------
    // Group data
    // -----------------------------------------------------------------

    private var groupIdToString: Array<String> = emptyArray()

    @Volatile
    private var pendingGroupResolver: ((Int) -> List<String>)? = null

    private fun syncGroupsInternal(graphNodes: List<GraphNode<Id, Data>>, n: Int) {
        val index = pendingGroupIndex

        if (index == null || !pendingGroupSettings.enabled || n == 0) {
            if (nodeGroupOffset.size < n + 1) {
                nodeGroupOffset = IntArray((n + 1).coerceAtLeast(1))
            }
            for (i in 0..n) nodeGroupOffset[i] = 0
            groupCount = 0
            lastGroupSignature = -1
            return
        }

        // Group id -> dense engine index. Order follows model.defs so it's stable.
        val groupIds = index.groupIds
        val g = groupIds.size
        val groupIndexOf = HashMap<GroupId, Int>(g * 2)
        for (gi in 0 until g) groupIndexOf[groupIds[gi]] = gi

        // Per-node resolved memberships (engine group index + weight), by node index.
        val perNodeIds = arrayOfNulls<IntArray>(n)
        val perNodeWeights = arrayOfNulls<FloatArray>(n)
        var totalEntries = 0

        for (i in 0 until n) {
            val memberships = index.membershipsOf(graphNodes[i].id)
            if (memberships.isEmpty()) continue
            // A membership whose groupId has no def is dropped (defensive).
            var count = 0
            for (m in memberships) if (groupIndexOf.containsKey(m.groupId)) count++
            if (count == 0) continue

            val gids = IntArray(count)
            val gws = FloatArray(count)
            var k = 0
            for (m in memberships) {
                val gi = groupIndexOf[m.groupId] ?: continue
                gids[k] = gi
                gws[k] = m.weight
                k++
            }
            perNodeIds[i] = gids
            perNodeWeights[i] = gws
            totalEntries += count
        }

        // Signature: group count, entry count, per-node membership shape, and
        // each group's id hash. Appearance (name/color) is NOT included — that
        // doesn't affect physics, so it must not trigger a reheat.
        var sig = g * 1_000_003 + totalEntries
        for (i in 0 until n) sig = sig * 31 + (perNodeIds[i]?.size ?: 0)
        for (gi in 0 until g) sig = sig * 31 + groupIds[gi].raw.hashCode()
        val isFirstSync = lastGroupSignature == -1
        val groupsChanged = sig != lastGroupSignature
        lastGroupSignature = sig

        if (nodeGroupOffset.size < n + 1) {
            nodeGroupOffset = IntArray((n + 1).coerceAtLeast(16))
        }
        if (nodeGroupId.size < totalEntries) {
            nodeGroupId = IntArray(totalEntries.coerceAtLeast(16))
            nodeGroupWeight = FloatArray(totalEntries.coerceAtLeast(16))
        }
        if (groupCentroidX.size < g) {
            val cap = g.coerceAtLeast(8)
            groupCentroidX = FloatArray(cap)
            groupCentroidY = FloatArray(cap)
            groupMemberCount = IntArray(cap)
            groupWeightSum = FloatArray(cap)
        }

        var w = 0
        for (i in 0 until n) {
            nodeGroupOffset[i] = w
            val gids = perNodeIds[i] ?: continue
            val gws = perNodeWeights[i]!!
            for (k in gids.indices) {
                nodeGroupId[w] = gids[k]
                nodeGroupWeight[w] = gws[k]
                w++
            }
        }
        nodeGroupOffset[n] = w
        groupCount = g
        groupIdToValue = Array(g) { groupIds[it] }

        if (groupsChanged && !isFirstSync && config.groupChangeReheatAlpha > 0f) {
            reheatInternal(config.groupChangeReheatAlpha)
        }
    }

    /** Snapshot of (groupId, [(x,y)...]) for hull computation. */
    override fun snapshotGroupsForHulls(): List<Pair<GroupId, FloatArray>> {
        val g = groupCount
        if (g == 0 || nodeCount == 0) return emptyList()

        val counts = IntArray(g)
        for (i in 0 until nodeCount) {
            val from = nodeGroupOffset[i]
            val to = nodeGroupOffset[i + 1]
            for (k in from until to) counts[nodeGroupId[k]]++
        }
        val pts = Array(g) { FloatArray(counts[it] * 2) }
        val wIdx = IntArray(g)
        for (i in 0 until nodeCount) {
            val from = nodeGroupOffset[i]
            val to = nodeGroupOffset[i + 1]
            val px = posX[i]; val py = posY[i]
            for (k in from until to) {
                val gid = nodeGroupId[k]
                val w = wIdx[gid]
                pts[gid][w] = px
                pts[gid][w + 1] = py
                wIdx[gid] = w + 2
            }
        }
        val out = ArrayList<Pair<GroupId, FloatArray>>(g)
        for (gi in 0 until g) {
            if (counts[gi] > 0) out.add(groupIdToValue[gi] to pts[gi])
        }
        return out
    }
}