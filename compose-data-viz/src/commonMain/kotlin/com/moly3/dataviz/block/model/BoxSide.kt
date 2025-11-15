package com.moly3.dataviz.block.model

enum class BoxSide { LEFT, TOP, RIGHT, BOTTOM }

val allSides = listOf(
    BoxSide.TOP,
    BoxSide.LEFT,
    BoxSide.RIGHT,
    BoxSide.BOTTOM,
)

fun BoxSide.reverse(): BoxSide {
    return when (this) {
        BoxSide.LEFT -> BoxSide.RIGHT
        BoxSide.TOP -> BoxSide.BOTTOM
        BoxSide.RIGHT -> BoxSide.LEFT
        BoxSide.BOTTOM -> BoxSide.TOP
    }
}