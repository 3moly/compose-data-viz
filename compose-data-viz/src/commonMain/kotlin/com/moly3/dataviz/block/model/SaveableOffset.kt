package com.moly3.dataviz.block.model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

@Serializable
data class SaveableOffset(
    val x: Float,
    val y: Float
) {
    operator fun plus(other: SaveableOffset): SaveableOffset {
        return SaveableOffset(
            x = this.x + other.x,
            y = this.y + other.y
        )
    }

    operator fun div(delta: Float): SaveableOffset {
        return SaveableOffset(
            x = this.x / delta,
            y = this.y / delta
        )
    }

    operator fun unaryMinus(): SaveableOffset {
        return SaveableOffset(-x, -y)
    }

    operator fun minus(other: SaveableOffset): SaveableOffset {
        return SaveableOffset(
            x = this.x - other.x,
            y = this.y - other.y
        )
    }

    operator fun times(factor: Float): SaveableOffset {
        return SaveableOffset(
            x = this.x * factor,
            y = this.y * factor
        )
    }

    override fun toString(): String {
        return "${x.toInt()}:${y.toInt()}"
    }

    companion object {
        val Zero = SaveableOffset(0f, 0f)
    }
}

fun Offset.getWorldPosition(): SaveableOffset {
    return SaveableOffset(
        x = this.x,
        y = this.y
    )
}

fun SaveableOffset.getValue(): Offset {
    return Offset(this.x, this.y)
}