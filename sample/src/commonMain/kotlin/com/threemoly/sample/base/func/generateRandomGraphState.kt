package com.threemoly.sample.base.func

import androidx.compose.ui.graphics.Color
import com.threemoly.sample.base.graph.GraphState
import com.threemoly.sample.base.graph.ObsidianGraphData
import com.threemoly.sample.base.graph.ObsidianGraphNode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import com.moly3.dataviz.core.graph.model.ArrowHead
import com.moly3.dataviz.core.graph.model.ArrowStyle
import com.moly3.dataviz.core.graph.model.Connection
import com.moly3.dataviz.core.graph.model.LineStyle
import kotlin.random.Random

private val lineStyles = LineStyle.entries.toTypedArray()
private val arrowHeads = ArrowHead.entries.toTypedArray()

private fun randomConnectionStyle(rng: Random): ArrowStyle = ArrowStyle(
    line = lineStyles[rng.nextInt(lineStyles.size)],
    head = if (rng.nextFloat() < 0.3f) arrowHeads[rng.nextInt(arrowHeads.size)] else ArrowHead.None,
)

fun generateRandomGraphState(
    nodeCount: Int,
    maxBranchingFactor: Int = 3
): GraphState {
    if (nodeCount <= 0) return GraphState()

    val rng = Random.Default

    val nodes = (1..nodeCount).map { index ->
        ObsidianGraphNode(
            id = "node_$index",
            name = "Node $index",
            data = ObsidianGraphData.File("https://picsum.photos/id/${index}/300/300"),
            colorValue = randomColor().value
        )
    }.toMutableList()

    val singlers = (1..nodeCount).map { index ->
        ObsidianGraphNode(
            id = "node_single_$index",
            name = "Node sababaababab $index",
            data = ObsidianGraphData.File("https://picsum.photos/id/${index}/300/300"),
            colorValue = randomColor().value
        )
    }
    nodes.addAll(singlers)

    nodes += ObsidianGraphNode(id = "Folder", name = "Folder", data = ObsidianGraphData.Tag(1L))
    nodes += ObsidianGraphNode(id = "Cat", name = "Cat", data = ObsidianGraphData.Tag(1L))
    nodes += ObsidianGraphNode(id = "Share", name = "Share", data = ObsidianGraphData.Tag(1L))

    val nodeIds = nodes.map { it.id }

    // Track edges as (sourceId -> set of targetId) but with a per-edge style.
    // Use a single canonical style per undirected edge so the two directions agree.
    val connections = mutableMapOf<String, MutableList<Connection<String>>>()
    nodeIds.forEach { connections[it] = mutableListOf() }

    if (nodeCount > 1) {
        val unassignedNodes = nodeIds.drop(1).toMutableList()
        unassignedNodes.shuffle()

        val queue = ArrayDeque<String>()
        queue.addLast(nodeIds.first())

        while (unassignedNodes.isNotEmpty() && queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            val childrenCount = (1..maxBranchingFactor).random().coerceAtMost(unassignedNodes.size)

            for (i in 0 until childrenCount) {
                val child = unassignedNodes.removeFirst()

                // One style for this undirected edge, mirrored on the reverse direction
                // (headStart/headEnd swap so the arrowheads point the same way visually).
                val style = randomConnectionStyle(rng)
                val reverseStyle = style.copy(
                    head = style.head,
                )

                connections[parent]?.add(
                    Connection(
                        target = child,
                        style = style.copy(
                            head = ArrowHead.None,
                            line = LineStyle.Solid,
                            color = Color.Red
                        )
                    )
                )
//                connections[child]?.add(Connection(target = parent, style = reverseStyle.copy(color =  Color.Green)))

                queue.addLast(child)
            }
        }
    }

    val persistentConnections = connections.mapValues { (_, edges) ->
        persistentListOf(*edges.toTypedArray())
    }.toPersistentMap()

    return GraphState(
        graphNodes = persistentListOf(*nodes.toTypedArray()),
        connections = persistentConnections
    )
}