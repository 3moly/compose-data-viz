package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset

fun getMapPosition(
    position: Offset,
    centerOfScreen: Offset,
    zoom: Float,
    userCoordinate: Offset
): Offset {
    val position = (position - centerOfScreen) / zoom + userCoordinate
    return position
}
