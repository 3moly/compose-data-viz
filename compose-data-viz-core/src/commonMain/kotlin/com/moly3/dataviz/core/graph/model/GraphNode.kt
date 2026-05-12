package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
@Stable
data class GraphNode<Id, Data>(
    val id: Id,
    val name: String,
    val data: Data,
    val colorValue: ULong? = null
) {
    companion object {
        fun getCircleSize(circleRadius: Float, connectionCount: Int, multiplier: Float?): Float {
            return if (multiplier != null) {
                circleRadius + multiplier * connectionCount.coerceIn(0, 30)
            } else {
                circleRadius
            }
        }
    }
}