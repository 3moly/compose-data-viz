package com.moly3.dataviz.graph.ui

import androidx.compose.ui.graphics.Color
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.func.darker
//
//fun <Id, Data> getNodeColor(
//    node: GraphNode<Id, Data>,
//    cursorId: Id?,
//    draggedId: Id?,
//    connections: Set<Id>,
//    baseCircleColor: Color,
//    darkerFactor: Float
//): Color {
//    val base = when {
//        node.id == draggedId -> Color.Green
//        node.colorValue != null -> Color(node.colorValue!!)
//        else -> baseCircleColor
//    }
//    return when {
//        node.id == cursorId -> Color.Green
//        cursorId != null -> if (connections.contains(node.id)) base else base.darker(darkerFactor)
//        else -> base
//    }
//}