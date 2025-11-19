package com.moly3.dataviz.core.block.model

import androidx.compose.ui.geometry.Offset

data class SelectedConnection(
    val startPoint: Offset,
    val endPoint: Offset,
    val connection: ArcConnection
) {

    fun getMenuCenter(): Offset {
        val offset1 = startPoint
        val offset2 = endPoint
        val centerX = (offset1.x + offset2.x) / 2f
        val highestY = minOf(offset1.y, offset2.y)
        return Offset(centerX, highestY)
    }
}