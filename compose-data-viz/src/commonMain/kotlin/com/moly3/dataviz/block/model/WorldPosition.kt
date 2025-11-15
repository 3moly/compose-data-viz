package com.moly3.dataviz.block.model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

@Serializable
data class WorldPosition(
    val x: Float,
    val y: Float
){
    override fun toString(): String {
        return "${x.toInt()}:${y.toInt()}"
    }
}

fun Offset.getWorldPosition(): WorldPosition {
    return WorldPosition(
        x = this.x,
        y = this.y
    )
}

fun WorldPosition.getValue(): Offset {
    return Offset(this.x, this.y)
}