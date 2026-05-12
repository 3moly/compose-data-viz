package com.threemoly.sample.base.func

import com.threemoly.sample.base.graph.GraphState
import com.threemoly.sample.base.graph.ObsidianGraphData
import com.threemoly.sample.base.graph.ObsidianGraphNode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap

fun generateRandomGraphState(
    nodeCount: Int,
    maxBranchingFactor: Int = 3 // Controls how "wide" the tree gets. 3 or 4 usually looks best.
): GraphState {
    // Handle edge case for empty graphs
    if (nodeCount <= 0) return GraphState()

    val nodes = (1..nodeCount).map { index ->
        ObsidianGraphNode(
            id = "node_$index",
            name = "Node $index",
            data = ObsidianGraphData.File(""),
            colorValue = randomColor().value
        )
    }

    val nodeIds = nodes.map { it.id }

    val connections = mutableMapOf<String, MutableSet<String>>()
    nodeIds.forEach { connections[it] = mutableSetOf() }

    // Only build connections if we have more than 1 node
    if (nodeCount > 1) {
        // Pool of nodes waiting to be attached to the tree
        val unassignedNodes = nodeIds.drop(1).toMutableList()
        unassignedNodes.shuffle() // Shuffle to ensure a different tree shape every time

        // Queue for breadth-first tree generation
        val queue = ArrayDeque<String>()
        queue.addLast(nodeIds.first()) // Start with the Root node

        while (unassignedNodes.isNotEmpty() && queue.isNotEmpty()) {
            val parent = queue.removeFirst()

            // Give this parent a random number of children up to the max factor
            // (coerceAtMost ensures we don't try to attach more nodes than we have left)
            val childrenCount = (1..maxBranchingFactor).random().coerceAtMost(unassignedNodes.size)

            for (i in 0 until childrenCount) {
                val child = unassignedNodes.removeFirst()

                // Connect them bidirectionally
                connections[parent]?.add(child)
                connections[child]?.add(parent)

                // Add child to the queue so it can eventually spawn its own children
                queue.addLast(child)
            }
        }
    }

    // Map to Immutable State
    val persistentConnections = connections.mapValues { (_, edges) ->
        persistentListOf(*edges.toTypedArray())
    }.toPersistentMap()

    return GraphState(
        graphNodes = persistentListOf(*nodes.toTypedArray()),
        connections = persistentConnections
    )
}