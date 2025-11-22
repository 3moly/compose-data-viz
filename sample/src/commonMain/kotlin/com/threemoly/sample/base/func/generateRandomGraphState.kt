package com.threemoly.sample.base.func

import com.threemoly.sample.base.graph.GraphState
import com.threemoly.sample.base.graph.ObsidianGraphData
import com.threemoly.sample.base.graph.ObsidianGraphNode
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

fun generateRandomGraphState(
    nodeCount: Int,
    connectionsPercentPerNode: Float
): GraphState {

    val nodes = (1..nodeCount).map { index ->
        ObsidianGraphNode(
            id = "node_$index",
            name = "Node $index",
            data = ObsidianGraphData.File(""),
            colorValue = randomColor().value
        )
    }

    val nodeIds = nodes.map { it.id }
    val connections = mutableMapOf<String, PersistentList<String>>()

    nodeIds.forEach { nodeId ->
        val maxConnections = (nodeCount - 1) // max possible connections (excluding self)
        val targetConnectionCount = (maxConnections * connectionsPercentPerNode / 100f).toInt()

        // Randomly select target nodes to connect to
        val possibleTargets = nodeIds.filter { it != nodeId }.shuffled()
        val connectedNodes = possibleTargets.take(targetConnectionCount)

        connections[nodeId] = persistentListOf(*connectedNodes.toTypedArray())
    }

    return GraphState(
        graphNodes = persistentListOf(*nodes.toTypedArray()),
        connections = persistentMapOf(*makeBidirectional(connections).toList().toTypedArray())
    )
}