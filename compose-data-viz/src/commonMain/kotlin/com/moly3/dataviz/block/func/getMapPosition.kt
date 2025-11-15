package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.model.WorldPosition
import com.moly3.dataviz.block.model.getWorldPosition

fun getMapPosition(
    position: Offset,
    centerOfScreen: Offset,
    zoom: Float,
    userCoordinate: Offset
): WorldPosition {
    val position = (position - centerOfScreen) / zoom + userCoordinate
    return position.getWorldPosition()
}
