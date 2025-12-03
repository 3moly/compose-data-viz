package com.moly3.dataviz.whiteboard.func

import com.moly3.dataviz.core.whiteboard.model.BoxSide

fun BoxSide.reverse(): BoxSide {
    return when (this) {
        BoxSide.LEFT -> BoxSide.RIGHT
        BoxSide.TOP -> BoxSide.BOTTOM
        BoxSide.RIGHT -> BoxSide.LEFT
        BoxSide.BOTTOM -> BoxSide.TOP
    }
}