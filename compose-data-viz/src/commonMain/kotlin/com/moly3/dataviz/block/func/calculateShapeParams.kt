package com.moly3.dataviz.block.func

import androidx.compose.ui.unit.dp
import com.moly3.dataviz.block.minShapeSize
import com.moly3.dataviz.block.model.Coords
import com.moly3.dataviz.block.model.DragAction
import com.moly3.dataviz.block.model.DragType
import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.model.Shape
import com.moly3.dataviz.block.model.ShapeParams

fun calculateShapeParams(
    item: Shape,
    zoom: Float,
    density: Float,
    userCoordinate: Offset,
    dragAction: DragAction?,
    roundToNearest: Int?
): ShapeParams {
    val itemPosition = if (dragAction != null
    ) {
        if (dragAction.dragType is DragType.Shape &&
            dragAction.dragType.shapeId == item.id
        ) {
            dragAction.accelerate
        } else if (
            dragAction.dragType is DragType.Resize &&
            dragAction.dragType.shapeId == item.id
        ) {
            val resizeType = dragAction.dragType.type
            val accelerate = dragAction.accelerate
            val resizePosition = resizePosition(accelerate, resizeType)
            (item.position + resizePosition)
        } else
            item.position
    } else
        item.position

    val addOffset = if (dragAction != null &&
        dragAction.dragType is DragType.Resize &&
        dragAction.dragType.shapeId == item.id
    ) {
        val resizeType = dragAction.dragType.type
        val accelerate = dragAction.accelerate

        resizeSize(accelerate, resizeType, roundToNearest = roundToNearest)
    } else {
        Offset.Zero
    }
    val itemSize = (item.size + addOffset).keepCurrentOrMin(minShapeSize)
    val offset2 =
        ((userCoordinate + (-itemPosition - (itemSize / 2f))) * -1f * zoom) / density
    val offset = Coords(offset2.x.dp, offset2.y.dp)
    val itemWidth = (itemSize.x * zoom / density).dp
    val itemHeight = (itemSize.y * zoom / density).dp

    return ShapeParams(
        itemPosition = itemPosition,
        offset = offset,
        itemWidth = itemWidth,
        itemHeight = itemHeight
    )
}