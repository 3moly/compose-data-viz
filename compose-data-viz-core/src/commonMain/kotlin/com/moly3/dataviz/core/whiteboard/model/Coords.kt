package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

@Stable
data class Coords(
    val x: Dp,
    val y: Dp,
)

fun Coords.toOffset(): Offset {
    return Offset(x.value, y.value)
}