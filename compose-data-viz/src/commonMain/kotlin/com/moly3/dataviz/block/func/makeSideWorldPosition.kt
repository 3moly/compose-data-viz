package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.model.BoxSide
import com.moly3.dataviz.block.model.WorldPosition
import com.moly3.dataviz.block.model.getValue
import com.moly3.dataviz.block.model.getWorldPosition

fun makeSideWorldPosition(
    itemPosition: WorldPosition,
    boxSize: Offset,
    side: BoxSide
): WorldPosition {
    val sideOffset = when (side) {
        BoxSide.LEFT -> Offset(-boxSize.x / 2, 0f)
        BoxSide.TOP -> Offset(0f, -boxSize.y / 2)
        BoxSide.RIGHT -> Offset(boxSize.x / 2, 0f)
        BoxSide.BOTTOM -> Offset(0f, boxSize.y / 2)
    }
    return (itemPosition.getValue() + sideOffset + boxSize / 2f).getWorldPosition()
}