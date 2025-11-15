package com.moly3.dataviz.graph.model

import kotlinx.serialization.Serializable

@Serializable
data class GraphNode<Id, Data>(
    val id: Id,
    val name: String,
    val data: Data,
    val colorValue: ULong? = null
) {
    companion object {
        fun getCircleSize(circleRadius: Float, connectionCount: Int): Float {
            return circleRadius + 1 * connectionCount.coerceIn(0, 30)
        }
    }
}