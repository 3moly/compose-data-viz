package com.moly3.dataviz.block.func

import com.moly3.dataviz.block.model.BoxSide
import androidx.compose.ui.geometry.Offset

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