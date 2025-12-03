package com.moly3.dataviz.whiteboard.func


import androidx.compose.ui.input.pointer.PointerIcon
import com.moly3.dataviz.core.whiteboard.model.PointerIconType

actual fun getPointerIcon(type: PointerIconType): PointerIcon {
    return PointerIcon.Default
}