package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.whiteboard.model.Coords
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.ShapeParams
import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.whiteboard.minShapeSize

fun <Id> calculateShapeParams(
    item: Shape<Id>,
    zoom: Float,
    density: Float,
    userCoordinate: Offset,
    dragAction: DragAction<Id>?,
    roundToNearest: Int?
): ShapeParams {
    val itemPosition = if (dragAction != null
    ) {
        if (dragAction.dragType is DragType.ShapeDrag<Id> &&
            (dragAction.dragType as DragType.ShapeDrag<Id>).shapeId == item.id
        ) {
            dragAction.accelerate
        } else if (
            dragAction.dragType is DragType.Resize<Id> &&
            (dragAction.dragType as DragType.Resize<Id>).shapeId == item.id
        ) {
            val resizeType = (dragAction.dragType as DragType.Resize<Id>).type
            val accelerate = dragAction.accelerate
            val resizePosition =
                resizePosition(
                    accelerate,
                    resizeType,
                    roundToNearest
                )
            (item.position + resizePosition)
        } else
            item.position
    } else
        item.position

    val addOffset = if (dragAction != null &&
        dragAction.dragType is DragType.Resize<Id> &&
        (dragAction.dragType as DragType.Resize<Id>).shapeId == item.id
    ) {
        val resizeType = (dragAction.dragType as DragType.Resize<Id>).type
        val accelerate = dragAction.accelerate

        resizeSize(
            accelerate,
            resizeType,
            roundToNearest = roundToNearest
        )
    } else {
        Offset.Zero
    }
    val itemSize = (item.size + addOffset).keepCurrentOrMin(minShapeSize)
    val offset2 =
        ((userCoordinate + (-itemPosition - (itemSize / 2f))) * -1f * zoom) / density
    val offset = Coords(offset2.x.dp, offset2.y.dp)

    return ShapeParams(
        itemPosition = itemPosition,
        offset = offset,
        size = itemSize * zoom / density
    )
}