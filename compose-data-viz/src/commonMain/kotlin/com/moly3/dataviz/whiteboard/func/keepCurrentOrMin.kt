package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset

fun Offset.keepCurrentOrMin(minSize: Float): Offset {
    return this.copy(
        x = if (minSize <= x) x else minSize,
        y = if (minSize <= y) y else minSize,
    )
}