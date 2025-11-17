package com.moly3.dataviz.block.func

import com.moly3.dataviz.block.model.ResizeType
import com.moly3.dataviz.block.model.SaveableOffset

fun resizeSize(
    accelerate: SaveableOffset,
    resizeType: ResizeType,
    roundToNearest: Int?
): SaveableOffset {
    return when (resizeType) {
        ResizeType.TopLeft -> accelerate.copy(
            x = -accelerate.x,
            y = -accelerate.y
        )

        ResizeType.TopRight -> accelerate.copy(y = -accelerate.y)
        ResizeType.BottomLeft -> accelerate.copy(x = -accelerate.x)

        ResizeType.BottomRight -> accelerate
        ResizeType.Bottom -> accelerate.copy(x = 0f)
        ResizeType.Right -> accelerate.copy(y = 0f)
        ResizeType.Top -> accelerate.copy(
            x = 0f,
            y = -accelerate.y
        )

        ResizeType.Left -> accelerate.copy(
            y = 0f,
            x = -accelerate.x
        )
    }.roundToNearest(roundToNearest)
}