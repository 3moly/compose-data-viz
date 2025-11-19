package com.moly3.dataviz.graph.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.dataviz.graph.ui.DragNodeData
import kotlin.math.*
import kotlinx.coroutines.*

// Fast math with safety bounds (keeping performance)
object HybridMath {
    const val MAX_SAFE_VALUE = 1e5f
    const val MIN_DISTANCE = 0.01f

    fun safeFloat(value: Float): Float {
        return when {
            value.isNaN() -> 0f
            value.isInfinite() -> if (value > 0) MAX_SAFE_VALUE else -MAX_SAFE_VALUE
            value > MAX_SAFE_VALUE -> MAX_SAFE_VALUE
            value < -MAX_SAFE_VALUE -> -MAX_SAFE_VALUE
            else -> value
        }
    }

    fun safeDistance(dx: Float, dy: Float): Float {
        val distSq = dx * dx + dy * dy
        return max(sqrt(distSq), MIN_DISTANCE)
    }

    // Fast but smooth force limiting (non-linear)
    fun smoothLimit(force: Offset, maxForce: Float): Offset {
        val magnitude = sqrt(force.x * force.x + force.y * force.y)
        if (magnitude <= maxForce || magnitude == 0f) return force

        // Smooth sigmoid-like limiting instead of hard capping
        val ratio = maxForce / magnitude
        val smoothRatio = 0.5f + 0.5f * tanh((ratio - 0.5f) * 3f) // Smooth S-curve
        return force * smoothRatio
    }
}

// Optimized spatial grid (keeping performance)
class HybridSpatialGrid(
    private val cellSize: Float = 150f,
    private val bounds: Float = 3000f
) {
    private val grid = mutableMapOf<Long, MutableList<Int>>()
    private val gridWidth = max(10, (bounds * 2 / cellSize).toInt())

    fun clear() = grid.clear()

    private fun hash(x: Float, y: Float): Long {
        val gx = ((x + bounds) / cellSize).toInt().coerceIn(0, gridWidth - 1)
        val gy = ((y + bounds) / cellSize).toInt().coerceIn(0, gridWidth - 1)
        return gx.toLong() shl 16 or gy.toLong()
    }

    fun addNode(index: Int, position: Offset) {
        val h = hash(position.x, position.y)
        grid.getOrPut(h) { mutableListOf() }.add(index)
    }

    fun getNearbyNodes(position: Offset, maxResults: Int = 80): List<Int> {
        val result = mutableListOf<Int>()
        val centerX = ((position.x + bounds) / cellSize).toInt()
        val centerY = ((position.y + bounds) / cellSize).toInt()

        for (dx in -1..1) {
            for (dy in -1..1) {
                val gx = (centerX + dx).coerceIn(0, gridWidth - 1)
                val gy = (centerY + dy).coerceIn(0, gridWidth - 1)
                val h = gx.toLong() shl 16 or gy.toLong()
                grid[h]?.let { result.addAll(it) }
                if (result.size >= maxResults) break
            }
            if (result.size >= maxResults) break
        }
        return result.take(maxResults)
    }
}

data class HybridNode<Id>(
    val id: Id,
    var position: Offset,
    var velocity: Offset = Offset.Zero,
    val connections: IntArray,
    val connectionCount: Int,
    val connectivityScaling: Float
)

class HybridForceCalculator<Id, Data>(
    private val spatialGrid: HybridSpatialGrid = HybridSpatialGrid()
) {
    private val nodes = mutableListOf<HybridNode<Id>>()
    private val nodeMap = mutableMapOf<Id, Int>()

    fun initialize(
        graphNodes: List<GraphNode<Id, Data>>,
        coordinates: Map<Id, Offset>,
        velocities: Map<Id, Offset>,
        connections: Map<Id, List<Id>>
    ) {
        nodes.clear()
        nodeMap.clear()
        spatialGrid.clear()

        graphNodes.forEachIndexed { index, node ->
            nodeMap[node.id] = index
        }

        graphNodes.forEachIndexed { index, node ->
//            val position = coordinates[node.id] ?: Offset.Zero
            val position = coordinates[node.id] ?: Offset(
                (index * 73f) % 400f - 200f,
                (index * 47f) % 300f - 150f
            )
            val velocity = velocities[node.id] ?: Offset.Zero
            val nodeConnections = connections[node.id] ?: emptyList()
            val connectionIndices = nodeConnections.mapNotNull { nodeMap[it] }.toIntArray()

            // Your original connectivity scaling (keeping the feel)
            val connectivityScaling = 1f / (1f + ln(max(1f, nodeConnections.size.toFloat())) * 0.3f)

            val hybridNode = HybridNode(
                id = node.id,
                position = position,
                velocity = velocity,
                connections = connectionIndices,
                connectionCount = connectionIndices.size,
                connectivityScaling = connectivityScaling
            )

            nodes.add(hybridNode)
            spatialGrid.addNode(index, position)
        }
    }

    fun calculateHybridForce(
        nodeIndex: Int,
        settings: GraphViewSettings,
        draggedId: Id?
    ): Offset {
        if (nodeIndex >= nodes.size) return Offset.Zero

        val currentNode = nodes[nodeIndex]
        if (currentNode.id == draggedId) return Offset.Zero

        var netForce = Offset.Zero
        val currentPos = currentNode.position

        // === SPATIAL OPTIMIZATION (Performance) + YOUR ORIGINAL LOGIC (Feel) ===
        val maxInteractionDistance = settings.linkDistance * 3f
        val nearbyIndices = if (currentNode.connectionCount > 50) {
            // Use spatial grid for performance
            spatialGrid.getNearbyNodes(currentPos, 60)
                .filter { it != nodeIndex && it < nodes.size }
        } else {
            // Use all nodes for better clustering (your original approach)
            nodes.indices.filter { it != nodeIndex }
        }

        // === REPULSION FORCES (Your original approach with performance tweaks) ===
        for (otherIndex in nearbyIndices) {
            val otherNode = nodes[otherIndex]
            val directionVector = otherNode.position - currentPos
            var distance = HybridMath.safeDistance(directionVector.x, directionVector.y)
            val softening = settings.circleSize * 0.5f

            // Your original deterministic displacement for identical coordinates
            val normalizedDirection = if (distance < 0.01f) {
                val hash1 = currentNode.id.hashCode()
                val hash2 = otherNode.id.hashCode()
                val combinedHash = hash1 xor hash2
                val angle = (combinedHash % 360) * (PI / 180.0)
                distance = 0.1f
                Offset(cos(angle).toFloat(), sin(angle).toFloat())
            } else {
                directionVector / distance
            }

            distance = max(distance, 0.1f)

            // Your original repulsion calculation (keeping the feel)
            val baseForceDivisor = (distance + softening) * (distance + softening)
            var repulsionMagnitude = settings.repelForce / baseForceDivisor

            // Your original connection-based scaling
            val isCurrentConnectedToOther = currentNode.connections.contains(otherIndex)
            val isOtherConnectedToCurrent = otherNode.connections.contains(nodeIndex)
            val areMutuallyConnected = isCurrentConnectedToOther && isOtherConnectedToCurrent

            repulsionMagnitude *= when {
                areMutuallyConnected -> settings.mutualConnectionRepulsionMultiplier
                isCurrentConnectedToOther || isOtherConnectedToCurrent -> settings.connectedRepulsionMultiplier
                else -> settings.unconnectedRepulsionMultiplier
            }

            // Your original distance-based falloff
            if (distance > maxInteractionDistance * 0.5f) {
                val falloff =
                    exp(-(distance - maxInteractionDistance * 0.5f) / maxInteractionDistance)
                repulsionMagnitude *= falloff
            }

            // Your original connectivity scaling
            repulsionMagnitude *= currentNode.connectivityScaling

            netForce -= normalizedDirection * HybridMath.safeFloat(repulsionMagnitude)
        }

        // === LINK FORCES (Your original with performance limit) ===
        var linkForceAccumulator = Offset.Zero
        val maxLinksToProcess = min(currentNode.connectionCount, 100) // Performance limit

        for (i in 0 until maxLinksToProcess) {
            val connectedIndex = currentNode.connections[i]
            if (connectedIndex >= nodes.size) continue

            val connectedNode = nodes[connectedIndex]
            val directionVector = connectedNode.position - currentPos
            var distance = HybridMath.safeDistance(directionVector.x, directionVector.y)

            val normalizedDirection = if (distance < 0.01f) {
                val hash1 = currentNode.id.hashCode()
                val hash2 = connectedNode.id.hashCode()
                val combinedHash = hash1 xor hash2
                val angle = (combinedHash % 360) * (PI / 180.0)
                distance = 0.1f
                Offset(cos(angle).toFloat(), sin(angle).toFloat())
            } else {
                directionVector / distance
            }

            distance = max(distance, 0.1f)

            // Your original smoother spring force with adaptive strength
            val naturalLinkDistance = settings.linkDistance
            var linkForceMagnitude = settings.linkForce *
                    ln((distance + 1f) / (naturalLinkDistance + 1f))

            // Your original connectivity-based reduction
            if (currentNode.connectionCount > 10) {
                linkForceMagnitude *= (1f / sqrt(currentNode.connectionCount.toFloat() / 10f))
            }

            // Your original long distance enhancement
            if (distance > naturalLinkDistance * 1.5f && linkForceMagnitude > 0) {
                linkForceMagnitude *= settings.longDistanceLinkMultiplier
            }

            linkForceAccumulator += normalizedDirection * HybridMath.safeFloat(linkForceMagnitude)
        }

        netForce += linkForceAccumulator

        // === CENTERING FORCE (Your original) ===
        val distanceToCenter = HybridMath.safeDistance(currentPos.x, currentPos.y)
        if (distanceToCenter > 0.1f) {
            val centeringForceMagnitude = settings.centerForce *
                    (distanceToCenter * currentNode.connectivityScaling)
            netForce -= currentPos * (centeringForceMagnitude / distanceToCenter)
        }

        // === FORCE LIMITING (Your original smooth approach) ===
        val totalForceMagnitude = sqrt(netForce.x * netForce.x + netForce.y * netForce.y)

        // Your original adaptive max force
        val adaptiveMaxForce = settings.maxForce *
                (if (currentNode.connectionCount > 20) 0.5f else 1f)

        if (totalForceMagnitude > adaptiveMaxForce && adaptiveMaxForce > 0) {
            // Your original smooth clamping
            val clampingFactor = adaptiveMaxForce / totalForceMagnitude
            val smoothClamp = 0.3f + 0.7f * clampingFactor
            netForce *= smoothClamp
        }

        // Your original damping
        netForce = Offset(
            netForce.x * 0.92f,
            netForce.y * 0.92f
        )

        return HybridMath.smoothLimit(netForce, settings.maxForce * 2f)
    }

    fun getNode(index: Int): HybridNode<Id>? = if (index < nodes.size) nodes[index] else null
    fun getNodeCount(): Int = nodes.size
}

// Hybrid concurrent processing (Performance + Your original integration)
suspend fun <Id, Data> applyHybridForces(
    graphNodes: List<GraphNode<Id, Data>>,
    connections: Map<Id, List<Id>>,
    settings: GraphViewSettings,
    coordinates: MutableMap<Id, Offset>,
    velocities: MutableMap<Id, Offset>,
    draggedNode: DragNodeData<Id>?,
    calculator: HybridForceCalculator<Id, Data>
) = coroutineScope {

    calculator.initialize(graphNodes, coordinates, velocities, connections)

    val nodeCount = calculator.getNodeCount()
    if (nodeCount == 0) return@coroutineScope

    val chunkSize = max(1, nodeCount / 6)
    val chunks = (0 until nodeCount).chunked(chunkSize)

    val updates = chunks.map { chunk ->
        async(Dispatchers.Default) {
            val localUpdates = mutableListOf<Triple<Id, Offset, Offset>>()

            for (nodeIndex in chunk) {
                val node = calculator.getNode(nodeIndex) ?: continue
                if (node.id == draggedNode?.id) continue

                val force = calculator.calculateHybridForce(nodeIndex, settings, draggedNode?.id)

                // Your original-style integration with safety
                val currentVel = node.velocity
                val acceleration = force * 0.1f // Your original scaling
                val newVelocity = (currentVel + acceleration) * 0.95f // Your original damping
                val newPosition = node.position + newVelocity * 0.5f

                localUpdates.add(Triple(node.id, newPosition, newVelocity))
            }
            localUpdates
        }
    }

    updates.awaitAll().flatten().forEach { (id, pos, vel) ->
        coordinates[id] = pos
        velocities[id] = vel
    }

    draggedNode?.let {
        if (it.offset != null)
            coordinates[it.id] = it.offset
        velocities[it.id] = Offset.Zero
    }
}

// Your original settings with performance tweaks


class HybridGraphRenderer<Id, Data> {
    private val calculator = HybridForceCalculator<Id, Data>()

    suspend fun updateGraph(
        nodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        draggedNode: DragNodeData<Id>?
    ) {
        applyHybridForces(
            nodes,
            connections,
            settings,
            coordinates,
            velocities,
            draggedNode,
            calculator
        )
    }
}