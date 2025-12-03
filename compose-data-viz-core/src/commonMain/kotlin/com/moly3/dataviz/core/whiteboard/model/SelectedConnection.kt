package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.geometry.Offset

data class SelectedConnection<Id>(
    val startPoint: Offset,
    val endPoint: Offset,
    val connection: ShapeConnection<Id>
) {

    fun getMenuCenter(): Offset {
        val offset1 = startPoint
        val offset2 = endPoint
        val centerX = (offset1.x + offset2.x) / 2f
        val highestY = minOf(offset1.y, offset2.y)
        return Offset(centerX, highestY)
    }
}