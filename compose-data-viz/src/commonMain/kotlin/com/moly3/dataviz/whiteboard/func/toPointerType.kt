package com.moly3.dataviz.whiteboard.func

import com.moly3.dataviz.core.whiteboard.model.PointerIconType
import com.moly3.dataviz.core.whiteboard.model.ResizeType

fun ResizeType.toPointerType(): PointerIconType {
    return when (this) {
        ResizeType.TopLeft -> PointerIconType.ResizeTopLeft
        ResizeType.TopRight -> PointerIconType.ResizeTopRight
        ResizeType.BottomLeft -> PointerIconType.ResizeBottomLeft
        ResizeType.BottomRight -> PointerIconType.ResizeBottomRight
        ResizeType.Bottom -> PointerIconType.ResizeVertical
        ResizeType.Right -> PointerIconType.ResizeHorizontal
        ResizeType.Top -> PointerIconType.ResizeVertical
        ResizeType.Left -> PointerIconType.ResizeHorizontal
    }
}