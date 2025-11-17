package com.moly3.dataviz.block.func

import com.moly3.dataviz.block.model.BoxSide
import com.moly3.dataviz.block.model.SaveableOffset

fun isInSidePosition(
    mousePosition: SaveableOffset,
    itemPosition: SaveableOffset,
    boxSize: SaveableOffset,
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