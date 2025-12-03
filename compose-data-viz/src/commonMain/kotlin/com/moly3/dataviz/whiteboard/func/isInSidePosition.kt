package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.whiteboard.model.BoxSide

fun isInSidePosition(
    mousePosition: Offset,
    itemPosition: Offset,
    boxSize: Offset,
    side: BoxSide,
    radius: Float
): Boolean {
    val sidePosition = makeSideWorldPosition(
        itemPosition = itemPosition,
        boxSize = boxSize,
        side = side
    )
    return isInCircle(
        pointX = sidePosition.x,
        pointY = sidePosition.y,
        centerX = mousePosition.x,
        centerY = mousePosition.y,
        radius = radius
    )
}