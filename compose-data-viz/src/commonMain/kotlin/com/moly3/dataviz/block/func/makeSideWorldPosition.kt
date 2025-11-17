package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.model.BoxSide
import com.moly3.dataviz.block.model.SaveableOffset
import com.moly3.dataviz.block.model.getValue
import com.moly3.dataviz.block.model.getWorldPosition

fun makeSideWorldPosition(
    itemPosition: SaveableOffset,
    boxSize: SaveableOffset,
    side: BoxSide
): SaveableOffset {
    val sideOffset = when (side) {
        BoxSide.LEFT -> SaveableOffset(-boxSize.x / 2, 0f)
        BoxSide.TOP -> SaveableOffset(0f, -boxSize.y / 2)
        BoxSide.RIGHT -> SaveableOffset(boxSize.x / 2, 0f)
        BoxSide.BOTTOM -> SaveableOffset(0f, boxSize.y / 2)
    }
    return (itemPosition + sideOffset + boxSize / 2f)
}