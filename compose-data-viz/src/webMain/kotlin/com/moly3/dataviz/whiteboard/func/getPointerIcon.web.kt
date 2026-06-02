package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword
import com.moly3.dataviz.core.whiteboard.model.PointerIconType

const val RESIZE_HORIZONTAL_KEYWORD = "w-resize"
const val RESIZE_VERTICAL_KEYWORD = "n-resize"
const val RESIZE_TOP_LEFT_KEYWORD = "nw-resize"
const val RESIZE_TOP_RIGHT_KEYWORD = "ne-resize"
const val RESIZE_BOTTOM_LEFT_KEYWORD = "sw-resize"
const val RESIZE_BOTTOM_RIGHT_KEYWORD = "se-resize"

@OptIn(ExperimentalComposeUiApi::class)
actual fun getPointerIcon(type: PointerIconType): PointerIcon =
    when (type) {
        PointerIconType.Default -> PointerIcon.Default
        PointerIconType.Hand -> PointerIcon.Hand
        PointerIconType.ResizeHorizontal -> PointerIcon.fromKeyword(RESIZE_HORIZONTAL_KEYWORD)
        PointerIconType.ResizeVertical -> PointerIcon.fromKeyword(RESIZE_VERTICAL_KEYWORD)
        PointerIconType.ResizeTopLeft -> PointerIcon.fromKeyword(RESIZE_TOP_LEFT_KEYWORD)
        PointerIconType.ResizeTopRight -> PointerIcon.fromKeyword(RESIZE_TOP_RIGHT_KEYWORD)
        PointerIconType.ResizeBottomLeft -> PointerIcon.fromKeyword(RESIZE_BOTTOM_LEFT_KEYWORD)
        PointerIconType.ResizeBottomRight -> PointerIcon.fromKeyword(RESIZE_BOTTOM_RIGHT_KEYWORD)
    }
