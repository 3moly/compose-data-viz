package com.moly3.dataviz.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult

fun TextLayoutResult.half(): Offset {
    val halfW = size.width.toFloat() / 2f
    val halfH = size.height.toFloat() / 2f
    return Offset(halfW, halfH)
}