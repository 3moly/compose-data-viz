package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.whiteboard.model.BoxSide

fun makeSideWorldPosition(
    itemPosition: Offset,
    boxSize: Offset,
    side: BoxSide
): Offset {
    val sideOffset = when (side) {
        BoxSide.LEFT -> Offset(-boxSize.x / 2, 0f)
        BoxSide.TOP -> Offset(0f, -boxSize.y / 2)
        BoxSide.RIGHT -> Offset(boxSize.x / 2, 0f)
        BoxSide.BOTTOM -> Offset(0f, boxSize.y / 2)
    }
    return (itemPosition + sideOffset + boxSize / 2f)
}