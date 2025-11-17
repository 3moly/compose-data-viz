package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.model.SaveableOffset
import com.moly3.dataviz.block.model.getWorldPosition

fun getMapPosition(
    position: SaveableOffset,
    centerOfScreen: SaveableOffset,
    zoom: Float,
    userCoordinate: SaveableOffset
): SaveableOffset {
    val position = (position - centerOfScreen) / zoom + userCoordinate
    return position
}
