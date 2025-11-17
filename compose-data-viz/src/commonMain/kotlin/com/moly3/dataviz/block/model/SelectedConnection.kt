package com.moly3.dataviz.block.model

import androidx.compose.ui.geometry.Offset

data class SelectedConnection(
    val startPoint: SaveableOffset,
    val endPoint: SaveableOffset,
    val connection: ArcConnection
) {

    fun getMenuCenter(): SaveableOffset {
        val offset1 = startPoint
        val offset2 = endPoint
        val centerX = (offset1.x + offset2.x) / 2f
        val highestY = minOf(offset1.y, offset2.y)
        return SaveableOffset(centerX, highestY)
    }
}

//data class SelectedShape(
//    val point: WorldPosition,
//    val shape: MapCard
//) {
//
//    fun getMenuCenter(): Offset {
//        val offset1 = point
//        //val offset2 = endPoint
//        val centerX = (offset1.x + offset1.x) / 2f
//        val highestY = minOf(offset1.y, offset1.y)
//        return Offset(centerX, highestY)
//    }
//}