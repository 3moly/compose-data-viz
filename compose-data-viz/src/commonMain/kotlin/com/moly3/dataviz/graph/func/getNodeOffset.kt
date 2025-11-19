package com.moly3.dataviz.graph.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.model.GraphNode

fun <Id, Data> GraphNode<Id, Data>.getNodeOffset(
    node: GraphNode<Id, Data> = this,
    coords: Map<Id, Offset>
): Offset? {
    val offset = coords[node.id]
    return offset
}