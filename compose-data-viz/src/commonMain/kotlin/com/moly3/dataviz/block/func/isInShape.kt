package com.moly3.dataviz.block.func

import com.moly3.dataviz.block.model.SaveableOffset
import com.moly3.dataviz.block.model.getValue

fun isInShape(
    mousePosition: SaveableOffset,
    shapePosition: SaveableOffset,
    shapeSize: SaveableOffset
): Boolean {
    val left = shapePosition.x
    val top = shapePosition.y
    val right = shapePosition.x + shapeSize.x
    val bottom = shapePosition.y + shapeSize.y
    val mouseX = mousePosition.getValue().x
    val mouseY = mousePosition.getValue().y

    val isHorizontallyInside = mouseX in left..right
    val isVerticallyInside = mouseY in top..bottom

    return isHorizontallyInside && isVerticallyInside
}