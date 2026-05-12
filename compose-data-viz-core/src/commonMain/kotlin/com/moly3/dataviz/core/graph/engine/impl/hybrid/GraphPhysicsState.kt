package com.moly3.dataviz.core.graph.engine.impl.hybrid;

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.model.GraphNode
import kotlin.math.ln
import kotlin.math.max

internal class GraphPhysicsState<Id> {
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
        var lo = start;
        var hi = end - 1
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