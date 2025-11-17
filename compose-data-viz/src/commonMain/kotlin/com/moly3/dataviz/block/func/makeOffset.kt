package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.minShapeSize
import com.moly3.dataviz.block.model.BoxSide
import com.moly3.dataviz.block.model.DragAction
import com.moly3.dataviz.block.model.DragType
import com.moly3.dataviz.block.model.Shape
import com.moly3.dataviz.block.model.SaveableOffset
import com.moly3.dataviz.block.model.getValue
import com.moly3.dataviz.block.model.getWorldPosition

fun makeSideOffset(
    dragAction: DragAction?,
    userCoordinate: SaveableOffset,
    boxSide: Shape,
    zoom: Float,
    side: BoxSide,
): SaveableOffset {

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
                (boxSide.position + resizePosition(
                    dragAction.accelerate,
                    dragAction.dragType.type
                ))
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
                    dragAction.accelerate,
                    dragAction.dragType.type,
                    roundToNearest = null
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
    itemPosition: SaveableOffset,
    userCoordinate: SaveableOffset,
    boxSize: SaveableOffset,
    zoom: Float,
    side: BoxSide
): SaveableOffset {
    val base = when (side) {
        BoxSide.LEFT -> SaveableOffset(-boxSize.x / 2, 0f)
        BoxSide.TOP -> SaveableOffset(0f, -boxSize.y / 2)
        BoxSide.RIGHT -> SaveableOffset(boxSize.x / 2, 0f)
        BoxSide.BOTTOM -> SaveableOffset(0f, boxSize.y / 2)
    }
    return ((userCoordinate + (-(itemPosition + base) - boxSize / 2f)) * -1f * zoom)
}

fun makeSideWorldOffset(
    itemPosition: SaveableOffset,
    boxSize: SaveableOffset,
    side: BoxSide
): SaveableOffset {
    val base = when (side) {
        BoxSide.LEFT -> Offset(0f, boxSize.y / 2f)
        BoxSide.TOP -> Offset(boxSize.x / 2, 0f)
        BoxSide.RIGHT -> Offset(boxSize.x, boxSize.y / 2f)
        BoxSide.BOTTOM -> Offset(boxSize.x / 2, boxSize.y)
    }
    return (itemPosition.getValue() + base).getWorldPosition()
}