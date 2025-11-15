package com.moly3.dataviz.block.func

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword
import com.moly3.dataviz.block.model.PointerIconType

@OptIn(ExperimentalComposeUiApi::class)
actual fun getPointerIcon(type: PointerIconType): PointerIcon {
    return when (type) {
        PointerIconType.Default -> PointerIcon.Default
        PointerIconType.Hand -> PointerIcon.Hand
        PointerIconType.ResizeHorizontal -> PointerIcon.fromKeyword("w-resize")
        PointerIconType.ResizeVertical -> PointerIcon.fromKeyword("n-resize")
        PointerIconType.ResizeTopLeft -> PointerIcon.fromKeyword("nw-resize")
        PointerIconType.ResizeTopRight -> PointerIcon.fromKeyword("ne-resize")
        PointerIconType.ResizeBottomLeft -> PointerIcon.fromKeyword("sw-resize")
        PointerIconType.ResizeBottomRight -> PointerIcon.fromKeyword("se-resize")
    }
}