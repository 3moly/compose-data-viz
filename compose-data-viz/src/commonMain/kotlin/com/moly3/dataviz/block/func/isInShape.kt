package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset


fun isInShape(
    mousePosition: Offset,
    shapePosition: Offset,
    shapeSize: Offset
): Boolean {
    val left = shapePosition.x
    val top = shapePosition.y
    val right = shapePosition.x + shapeSize.x
    val bottom = shapePosition.y + shapeSize.y
    val mouseX = mousePosition.x
    val mouseY = mousePosition.y

    val isHorizontallyInside = mouseX in left..right
    val isVerticallyInside = mouseY in top..bottom

    return isHorizontallyInside && isVerticallyInside
}