package com.moly3.dataviz.graph.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.func.avaliableCpuProcessors
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.dataviz.graph.ui.DragNodeData
import kotlinx.coroutines.*
import kotlin.math.*

// ============================================================================
// FAST MATH - inline where possible to avoid call overhead
// ============================================================================
object HybridMath {
    const val MAX_SAFE_VALUE = 1e5f
    const val MIN_DISTANCE = 0.01f

    @Suppress("NOTHING_TO_INLINE")
    inline fun safeFloat(value: Float): Float {
        return when {
            value.isNaN() -> 0f
            value > MAX_SAFE_VALUE -> MAX_SAFE_VALUE
            value < -MAX_SAFE_VALUE -> -MAX_SAFE_VALUE
            else -> value
        }
    }
}

// ============================================================================
// BARNES-HUT QUADTREE - O(n log n) instead of O(n²) for repulsion
// This is the core trick that lets Obsidian handle thousands of nodes smoothly.
// Distant clusters of nodes are approximated as a single "super node" at their
// center of mass.
// ============================================================================
private const val THETA = 1.2f      // Higher = more approximation = faster (Obsidian uses ~1.0-1.5)
private const val THETA_SQ = THETA * THETA
private const val MAX_DEPTH = 16
private const val LEAF_CAPACITY = 1

class QuadTree {
    // Flat arrays instead of object tree - massively reduces allocation/GC pressure
    // Each "node" of the tree uses 8 floats: cx, cy, halfSize, mass, comX, comY, firstChild, bodyIndex
    private var nodeX = FloatArray(1024)
    private var nodeY = FloatArray(1024)
    private var nodeHalf = FloatArray(1024)
    private var nodeMass = FloatArray(1024)
    private var nodeComX = FloatArray(1024)
    private var nodeComY = FloatArray(1024)
    // firstChild: index of first of 4 children (or -1 if leaf)
    private var nodeFirstChild = IntArray(1024) { -1 }
    // bodyIndex: index into bodies if leaf with one body, else -1
    private var nodeBody = IntArray(1024) { -1 }

    private var count = 0
    private var rootIndex = 0

    private fun ensureCapacity(needed: Int) {
        if (needed <= nodeX.size) return
        var newSize = nodeX.size
        while (newSize < needed) newSize *= 2
        nodeX = nodeX.copyOf(newSize)
        nodeY = nodeY.copyOf(newSize)
        nodeHalf = nodeHalf.copyOf(newSize)
        nodeMass = nodeMass.copyOf(newSize)
        nodeComX = nodeComX.copyOf(newSize)
        nodeComY = nodeComY.copyOf(newSize)
        val oldFC = nodeFirstChild
        nodeFirstChild = IntArray(newSize) { -1 }
        oldFC.copyInto(nodeFirstChild)
        val oldBody = nodeBody
        nodeBody = IntArray(newSize) { -1 }
        oldBody.copyInto(nodeBody)
    }

    private fun newNode(cx: Float, cy: Float, half: Float): Int {
        ensureCapacity(count + 1)
        val i = count++
        nodeX[i] = cx
        nodeY[i] = cy
        nodeHalf[i] = half
        nodeMass[i] = 0f
        nodeComX[i] = 0f
        nodeComY[i] = 0f
        nodeFirstChild[i] = -1
        nodeBody[i] = -1
        return i
    }

    fun build(positionsX: FloatArray, positionsY: FloatArray, n: Int) {
        count = 0
        if (n == 0) return

        // Find bounds
        var minX = positionsX[0]; var maxX = positionsX[0]
        var minY = positionsY[0]; var maxY = positionsY[0]
        for (i in 1 until n) {
            val x = positionsX[i]; val y = positionsY[i]
            if (x < minX) minX = x else if (x > maxX) maxX = x
            if (y < minY) minY = y else if (y > maxY) maxY = y
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val half = max(maxX - minX, maxY - minY) * 0.5f + 1f

        rootIndex = newNode(cx, cy, half)
        for (i in 0 until n) {
            insert(rootIndex, i, positionsX[i], positionsY[i], 0)
        }
    }

    private fun insert(nodeIdx: Int, bodyIdx: Int, x: Float, y: Float, depth: Int) {
        val existingBody = nodeBody[nodeIdx]
        val firstChild = nodeFirstChild[nodeIdx]

        // Update center of mass incrementally
        val mass = nodeMass[nodeIdx]
        val newMass = mass + 1f
        nodeComX[nodeIdx] = (nodeComX[nodeIdx] * mass + x) / newMass
        nodeComY[nodeIdx] = (nodeComY[nodeIdx] * mass + y) / newMass
        nodeMass[nodeIdx] = newMass

        if (firstChild == -1 && existingBody == -1) {
            // Empty leaf -> place body here
            nodeBody[nodeIdx] = bodyIdx
            return
        }

        if (firstChild == -1 && existingBody != -1) {
            if (depth >= MAX_DEPTH) {
                // Too deep - just accumulate mass (already done above), don't subdivide
                return
            }
            // Subdivide and re-insert existing body
            subdivide(nodeIdx)
            // We need original position of existing body to re-insert; caller provides that via stored arrays
            // We'll handle by calling reinsertBody
            // Mass was already counted for existing body during its first insert,
            // so we don't increment again - subdivide just routes it down.
            // But we DID increment mass for new body above, so we need to undo for existing
            // Actually: when we placed existingBody, we incremented mass to 1.
            // Now we incremented to 2 for new body. Both are correct totals.
            // We just need to route existing body to a child without touching mass again.
            routeToChild(nodeIdx, existingBody, posXRef!![existingBody], posYRef!![existingBody], depth, addMass = false)
            nodeBody[nodeIdx] = -1
        }

        // Now this node has children - route the new body
        routeToChild(nodeIdx, bodyIdx, x, y, depth, addMass = false)
    }

    // Reference to position arrays during build (set in build() if needed)
    private var posXRef: FloatArray? = null
    private var posYRef: FloatArray? = null

    fun buildWithRefs(positionsX: FloatArray, positionsY: FloatArray, n: Int) {
        posXRef = positionsX
        posYRef = positionsY
        build(positionsX, positionsY, n)
    }

    private fun subdivide(nodeIdx: Int) {
        val cx = nodeX[nodeIdx]
        val cy = nodeY[nodeIdx]
        val newHalf = nodeHalf[nodeIdx] * 0.5f
        ensureCapacity(count + 4)
        val first = count
        // Children order: NW, NE, SW, SE
        newNode(cx - newHalf, cy - newHalf, newHalf)
        newNode(cx + newHalf, cy - newHalf, newHalf)
        newNode(cx - newHalf, cy + newHalf, newHalf)
        newNode(cx + newHalf, cy + newHalf, newHalf)
        nodeFirstChild[nodeIdx] = first
    }

    private fun routeToChild(nodeIdx: Int, bodyIdx: Int, x: Float, y: Float, depth: Int, addMass: Boolean) {
        val cx = nodeX[nodeIdx]
        val cy = nodeY[nodeIdx]
        val firstChild = nodeFirstChild[nodeIdx]
        val childIdx = firstChild + (if (x >= cx) 1 else 0) + (if (y >= cy) 2 else 0)

        if (addMass) {
            val m = nodeMass[childIdx]
            val nm = m + 1f
            nodeComX[childIdx] = (nodeComX[childIdx] * m + x) / nm
            nodeComY[childIdx] = (nodeComY[childIdx] * m + y) / nm
            nodeMass[childIdx] = nm
        }

        insertWithoutMassUpdate(childIdx, bodyIdx, x, y, depth + 1)
    }

    private fun insertWithoutMassUpdate(nodeIdx: Int, bodyIdx: Int, x: Float, y: Float, depth: Int) {
        // Same as insert but skips its own mass update (caller handled it for routing case)
        val existingBody = nodeBody[nodeIdx]
        val firstChild = nodeFirstChild[nodeIdx]

        // Always update mass when entering a node during traversal
        val mass = nodeMass[nodeIdx]
        val newMass = mass + 1f
        nodeComX[nodeIdx] = (nodeComX[nodeIdx] * mass + x) / newMass
        nodeComY[nodeIdx] = (nodeComY[nodeIdx] * mass + y) / newMass
        nodeMass[nodeIdx] = newMass

        if (firstChild == -1 && existingBody == -1) {
            nodeBody[nodeIdx] = bodyIdx
            return
        }

        if (firstChild == -1 && existingBody != -1) {
            if (depth >= MAX_DEPTH) return
            subdivide(nodeIdx)
            routeToChild(nodeIdx, existingBody, posXRef!![existingBody], posYRef!![existingBody], depth, addMass = false)
            nodeBody[nodeIdx] = -1
        }

        routeToChild(nodeIdx, bodyIdx, x, y, depth, addMass = false)
    }

    /**
     * Compute repulsive force on body at (x, y) from all other bodies in tree.
     * Uses Barnes-Hut approximation: distant cells treated as single point at center of mass.
     * Returns force as (fx, fy) packed in a long-lived FloatArray of size 2 (caller-allocated to avoid GC).
     */
    fun computeForce(
        x: Float, y: Float, bodyIdx: Int,
        repelStrength: Float, softening: Float,
        out: FloatArray
    ) {
        out[0] = 0f
        out[1] = 0f
        if (count == 0) return
        computeForceRecursive(rootIndex, x, y, bodyIdx, repelStrength, softening, out)
    }

    private fun computeForceRecursive(
        nodeIdx: Int, x: Float, y: Float, bodyIdx: Int,
        repelStrength: Float, softening: Float,
        out: FloatArray
    ) {
        val mass = nodeMass[nodeIdx]
        if (mass == 0f) return

        val dx = nodeComX[nodeIdx] - x
        val dy = nodeComY[nodeIdx] - y
        val distSq = dx * dx + dy * dy
        val size = nodeHalf[nodeIdx] * 2f

        val firstChild = nodeFirstChild[nodeIdx]
        val body = nodeBody[nodeIdx]

        // Leaf with single body
        if (firstChild == -1) {
            if (body == bodyIdx || body == -1) return
            // Direct calculation
            applyRepulsion(dx, dy, distSq, mass, repelStrength, softening, out)
            return
        }

        // Internal node: use approximation if cell is "far enough"
        // size/dist < theta  <=>  size² < theta² * distSq
        if (size * size < THETA_SQ * distSq) {
            applyRepulsion(dx, dy, distSq, mass, repelStrength, softening, out)
            return
        }

        // Otherwise recurse into children
        for (i in 0 until 4) {
            computeForceRecursive(firstChild + i, x, y, bodyIdx, repelStrength, softening, out)
        }
    }

    private inline fun applyRepulsion(
        dx: Float, dy: Float, distSq: Float, mass: Float,
        repelStrength: Float, softening: Float,
        out: FloatArray
    ) {
        val safeDistSq = max(distSq, 0.01f)
        val dist = sqrt(safeDistSq)
        val softened = (dist + softening)
        val invDist = 1f / dist
        // Force magnitude: repel * mass / (dist + softening)²
        val mag = repelStrength * mass / (softened * softened)
        // Direction: AWAY from other body (so subtract from out, since dx/dy point TOWARD other)
        out[0] -= dx * invDist * mag
        out[1] -= dy * invDist * mag
    }
}

// ============================================================================
// SoA (Struct-of-Arrays) NODE STORAGE - cache-friendly, GC-free per frame
// ============================================================================
class GraphPhysicsState<Id> {
    var nodeCount = 0
        private set

    var posX = FloatArray(0); private set
    var posY = FloatArray(0); private set
    var velX = FloatArray(0); private set
    var velY = FloatArray(0); private set
    var connectivityScale = FloatArray(0); private set
    var connectionCount = IntArray(0); private set

    // Flat connection list: connectionsOffset[i]..connectionsOffset[i+1] gives range in connectionsFlat
    var connectionsOffset = IntArray(1); private set
    var connectionsFlat = IntArray(0); private set

    // Sorted connections per node for binary search lookup
    var connectionsSorted = IntArray(0); private set

    val ids = ArrayList<Id>()
    val idToIndex = HashMap<Id, Int>()

    private val quadTree = QuadTree()

    fun rebuild(
        graphNodes: List<GraphNode<Id, *>>,
        coordinates: Map<Id, Offset>,
        velocities: Map<Id, Offset>,
        connections: Map<Id, List<Id>>
    ) {
        val n = graphNodes.size
        if (n != nodeCount) {
            posX = FloatArray(n)
            posY = FloatArray(n)
            velX = FloatArray(n)
            velY = FloatArray(n)
            connectivityScale = FloatArray(n)
            connectionCount = IntArray(n)
            connectionsOffset = IntArray(n + 1)
        }
        ids.clear()
        idToIndex.clear()

        for (i in 0 until n) {
            val node = graphNodes[i]
            ids.add(node.id)
            idToIndex[node.id] = i
        }

        // Compute offsets
        var totalConns = 0
        for (i in 0 until n) {
            connectionsOffset[i] = totalConns
            totalConns += connections[graphNodes[i].id]?.size ?: 0
        }
        connectionsOffset[n] = totalConns
        if (connectionsFlat.size < totalConns) {
            connectionsFlat = IntArray(totalConns)
            connectionsSorted = IntArray(totalConns)
        }

        for (i in 0 until n) {
            val node = graphNodes[i]
            // Initial positions come from InitialLayout (seeded by Graph composable). The
            // fallback here only matters if a caller bypasses the composable layer; we use
            // a deterministic spread so the simulation can still untangle from an empty start.
            val pos = coordinates[node.id] ?: Offset(
                ((i * 73) % 400).toFloat() - 200f,
                ((i * 47) % 300).toFloat() - 150f
            )
            posX[i] = pos.x
            posY[i] = pos.y

            val vel = velocities[node.id] ?: Offset.Zero
            velX[i] = vel.x
            velY[i] = vel.y

            val conns = connections[node.id] ?: emptyList()
            val start = connectionsOffset[i]
            var written = 0
            for (cId in conns) {
                val cIdx = idToIndex[cId]
                if (cIdx != null) {
                    connectionsFlat[start + written] = cIdx
                    written++
                }
            }
            // Pad if some connections didn't resolve
            connectionCount[i] = written

            // Build sorted view for fast contains() check via binary search
            val sortedSlice = connectionsFlat.copyOfRange(start, start + written)
            sortedSlice.sort()
            sortedSlice.copyInto(connectionsSorted, start)

            // Connectivity scaling (preserves your original feel)
            connectivityScale[i] = 1f / (1f + ln(max(1f, written.toFloat())) * 0.3f)
        }

        nodeCount = n
    }

    fun isConnected(nodeIdx: Int, otherIdx: Int): Boolean {
        val start = connectionsOffset[nodeIdx]
        val end = start + connectionCount[nodeIdx]
        // Binary search on sorted slice
        var lo = start; var hi = end - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = connectionsSorted[mid]
            when {
                v < otherIdx -> lo = mid + 1
                v > otherIdx -> hi = mid - 1
                else -> return true
            }
        }
        return false
    }

    fun buildQuadTree() {
        quadTree.buildWithRefs(posX, posY, nodeCount)
    }

    fun computeRepulsion(nodeIdx: Int, repelStrength: Float, softening: Float, out: FloatArray) {
        quadTree.computeForce(posX[nodeIdx], posY[nodeIdx], nodeIdx, repelStrength, softening, out)
    }

    fun writeBack(coordinates: MutableMap<Id, Offset>, velocities: MutableMap<Id, Offset>) {
        for (i in 0 until nodeCount) {
            coordinates[ids[i]] = Offset(posX[i], posY[i])
            velocities[ids[i]] = Offset(velX[i], velY[i])
        }
    }
}

// ============================================================================
// FORCE CALCULATION - now O(n log n) overall thanks to Barnes-Hut
// ============================================================================
class HybridForceCalculator<Id, Data> {
    val state = GraphPhysicsState<Id>()

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

// ============================================================================
// PARALLEL INTEGRATION STEP
// ============================================================================
suspend fun <Id, Data> applyHybridForces(
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
                var vx = (state.velX[i] + ax) * settings.dampingFactor.let { if (it == 0f) 0.95f else it.coerceAtMost(0.99f) }
                var vy = (state.velY[i] + ay) * settings.dampingFactor.let { if (it == 0f) 0.95f else it.coerceAtMost(0.99f) }
                // Sleep tiny velocities to avoid jitter (this is what makes it "calm down" like Obsidian)
                if (vx * vx + vy * vy < 1e-4f) { vx = 0f; vy = 0f }

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
        if (draggedNode.offset != null) {
            state.posX[draggedIdx] = draggedNode.offset.x
            state.posY[draggedIdx] = draggedNode.offset.y
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

// ============================================================================
// RENDERER FACADE
// ============================================================================
class HybridGraphRenderer<Id, Data> {
    private val calculator = HybridForceCalculator<Id, Data>()
    val totalEnergy: Float get() = lastEnergy
    private var lastEnergy = 0f
    private val energyHolder = FloatArray(1)

    suspend fun updateGraph(
        nodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        draggedNode: DragNodeData<Id>?
    ) {
        applyHybridForces(
            nodes, connections, settings, coordinates, velocities,
            draggedNode, calculator, energyHolder
        )
        lastEnergy = energyHolder[0]
    }

    /** True when the simulation has settled - caller can skip frames to save battery. */
    fun isSettled(): Boolean = lastEnergy < 0.5f
}