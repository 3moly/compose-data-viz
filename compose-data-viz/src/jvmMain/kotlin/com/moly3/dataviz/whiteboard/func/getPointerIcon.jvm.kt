package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.input.pointer.PointerIcon
import com.moly3.dataviz.core.whiteboard.model.PointerIconType
import java.awt.Cursor

actual fun getPointerIcon(type: PointerIconType): PointerIcon {
    return when (type) {
        PointerIconType.Default -> PointerIcon.Default
        PointerIconType.Hand -> PointerIcon.Hand
        PointerIconType.ResizeHorizontal -> PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR))
        PointerIconType.ResizeVertical -> PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
        PointerIconType.ResizeTopLeft -> PointerIcon(Cursor(Cursor.NW_RESIZE_CURSOR))
        PointerIconType.ResizeTopRight -> PointerIcon(Cursor(Cursor.NE_RESIZE_CURSOR))
        PointerIconType.ResizeBottomLeft -> PointerIcon(Cursor(Cursor.SW_RESIZE_CURSOR))
        PointerIconType.ResizeBottomRight -> PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR))
    }
}