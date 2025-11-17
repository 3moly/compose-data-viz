package com.moly3.dataviz.block.func

import com.moly3.dataviz.block.model.ResizeType
import com.moly3.dataviz.block.model.SaveableOffset

fun resizePosition(accelerate: SaveableOffset, resizeType: ResizeType): SaveableOffset {
    return when (resizeType) {
        ResizeType.TopLeft -> accelerate

        ResizeType.TopRight -> {
            accelerate.copy(x = 0f, y = accelerate.y)
        }

        ResizeType.BottomLeft -> {
            accelerate.copy(x = accelerate.x, y = 0f)
        }

        ResizeType.Top -> accelerate.copy(x = 0f)
        ResizeType.Left -> accelerate.copy(y = 0f)
        ResizeType.BottomRight,
        ResizeType.Bottom,
        ResizeType.Right -> SaveableOffset(0f, 0f)
    }.roundToNearest(25)
}