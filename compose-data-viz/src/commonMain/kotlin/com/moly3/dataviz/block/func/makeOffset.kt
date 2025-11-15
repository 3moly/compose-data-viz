package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.minShapeSize
import com.moly3.dataviz.block.model.BoxSide
import com.moly3.dataviz.block.model.DragAction
import com.moly3.dataviz.block.model.DragType
import com.moly3.dataviz.block.model.Shape
import com.moly3.dataviz.block.model.WorldPosition
import com.moly3.dataviz.block.model.getValue
import com.moly3.dataviz.block.model.getWorldPosition

fun makeSideOffset(
    dragAction: DragAction?,
    userCoordinate: Offset,
    boxSide: Shape,
    zoom: Float,
    side: BoxSide
): Offset {

    val itemPosition =
        if (dragAction != null
        ) {
            if (dragAction.dragType is DragType.Shape &&
                dragAction.dragType.shapeId == boxSide.id
            ) {
                dragAction.accelerate
            } else if (dragAction.dragType is DragType.Resize &&
                dragAction.dragType.shapeId == boxSide.id
            ) {
                (boxSide.position.getValue() + resizePosition(
                    dragAction.accelerate.getValue(),
                    dragAction.dragType.type
                )).getWorldPosition()
            } else boxSide.position
        } else
            boxSide.position

    val itemSize =
        if (dragAction != null) {
            if (dragAction.dragType is DragType.Shape &&
                dragAction.dragType.shapeId == boxSide.id
            ) {
                boxSide.size
            } else if (dragAction.dragType is DragType.Resize &&
                dragAction.dragType.shapeId == boxSide.id
            ) {
                (boxSide.size + resizeSize(
                    dragAction.accelerate.getValue(),
                    dragAction.dragType.type
                )).keepCurrentOrMin(minShapeSize)
            } else boxSide.size
        } else
            boxSide.size

    return makeSideOffset(
        itemPosition = itemPosition,
        userCoordinate = userCoordinate,
        boxSize = itemSize,
        zoom = zoom,
        side = side
    )
}

fun makeSideOffset(
    itemPosition: WorldPosition,
    userCoordinate: Offset,
    boxSize: Offset,
    zoom: Float,
    side: BoxSide
): Offset {
    val base = when (side) {
        BoxSide.LEFT -> Offset(-boxSize.x / 2, 0f)
        BoxSide.TOP -> Offset(0f, -boxSize.y / 2)
        BoxSide.RIGHT -> Offset(boxSize.x / 2, 0f)
        BoxSide.BOTTOM -> Offset(0f, boxSize.y / 2)
    }
    return ((userCoordinate + (-(itemPosition.getValue() + base) - boxSize / 2f)) * -1f * zoom)
}

fun makeSideWorldOffset(
    itemPosition: WorldPosition,
    boxSize: Offset,
    side: BoxSide
): WorldPosition {
    val base = when (side) {
        BoxSide.LEFT -> Offset(0f, boxSize.y / 2f)
        BoxSide.TOP -> Offset(boxSize.x / 2, 0f)
        BoxSide.RIGHT -> Offset(boxSize.x, boxSize.y / 2f)
        BoxSide.BOTTOM -> Offset(boxSize.x / 2, boxSize.y)
    }
    return (itemPosition.getValue() + base).getWorldPosition()
}