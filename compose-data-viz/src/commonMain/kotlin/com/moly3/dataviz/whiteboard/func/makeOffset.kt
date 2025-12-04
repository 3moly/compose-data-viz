package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.moly3.dataviz.whiteboard.minShapeSize
import com.moly3.dataviz.core.whiteboard.model.BoxSide
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape

fun <Id> makeSideOffset(
    dragAction: DragAction<Id>?,
    userCoordinate: Offset,
    boxSide: Shape<Id>,
    zoom: Float,
    side: BoxSide,
    roundToNearest: Int?
): Offset {

    val itemPosition =
        if (dragAction != null
        ) {
            if (dragAction.dragType is DragType.ShapeDrag &&
                (dragAction.dragType as DragType.ShapeDrag).shapeId == boxSide.id
            ) {
                dragAction.accelerate
            } else if (dragAction.dragType is DragType.Resize &&
                (dragAction.dragType as DragType.Resize).shapeId == boxSide.id
            ) {
                (boxSide.position + resizePosition(
                    dragAction.accelerate,
                    (dragAction.dragType as DragType.Resize).type,
                    roundToNearest = roundToNearest
                ))
            } else boxSide.position
        } else
            boxSide.position

    val itemSize =
        if (dragAction != null) {
            if (dragAction.dragType is DragType.ShapeDrag &&
                (dragAction.dragType as DragType.ShapeDrag).shapeId == boxSide.id
            ) {
                boxSide.size
            } else if (dragAction.dragType is DragType.Resize &&
                (dragAction.dragType as DragType.Resize).shapeId == boxSide.id
            ) {
                (boxSide.size + resizeSize(
                    dragAction.accelerate,
                    (dragAction.dragType as DragType.Resize).type,
                    roundToNearest = null
                )).keepCurrentOrMin(minShapeSize)
            } else boxSide.size
        } else
            boxSide.size

    return makeSideOffset(
        itemPosition = itemPosition,
        userCoordinate = userCoordinate,
        shapeSize = itemSize,
        zoom = zoom,
        side = side
    )
}

fun makeSideOffset(
    itemPosition: Offset,
    userCoordinate: Offset,
    shapeSize: Offset,
    zoom: Float,
    side: BoxSide
): Offset {
    val koef = 2f
    val base = when (side) {
        BoxSide.LEFT -> Offset(-shapeSize.x / koef, 0f)
        BoxSide.TOP -> Offset(0f, -shapeSize.y / koef)
        BoxSide.RIGHT -> Offset(shapeSize.x / koef, 0f)
        BoxSide.BOTTOM -> Offset(0f, shapeSize.y / koef)
    }
    return ((userCoordinate + (-(itemPosition  + base) - shapeSize)) * -1f * zoom)
}

// Упрощённая понятная функция: возвращает смещение в пикселях (Offset)
fun makeSideOffsetPx(
    itemPosition: Offset,    // позиция центра элемента в px
    userCoordinate: Offset,  // координата пользовательского смещения/скролла в px
    boxSize: Offset,         // размер коробки элемента в px
    zoom: Float,
    side: BoxSide
): Offset {
    val base = when (side) {
        BoxSide.LEFT -> Offset(-boxSize.x / 2f, 0f)
        BoxSide.TOP -> Offset(0f, -boxSize.y / 2f)
        BoxSide.RIGHT -> Offset(boxSize.x / 2f, 0f)
        BoxSide.BOTTOM -> Offset(0f, boxSize.y / 2f)
    }

    // точка ручки (в px)
    val handlePos = itemPosition + base

    // вектор от userCoordinate до ручки
    val relative = handlePos - userCoordinate

    // применяем zoom к относительному вектору
    val scaled = relative * zoom

    // возвращаем scaled — это смещение в px от (0,0) канваса, которое потом конвертируем в dp
    return scaled
}


fun makeSideWorldOffset(
    itemPosition: Offset,
    boxSize: Offset,
    side: BoxSide
): Offset {
    val base = when (side) {
        BoxSide.LEFT -> Offset(0f, boxSize.y / 2f)
        BoxSide.TOP -> Offset(boxSize.x / 2, 0f)
        BoxSide.RIGHT -> Offset(boxSize.x, boxSize.y / 2f)
        BoxSide.BOTTOM -> Offset(boxSize.x / 2, boxSize.y)
    }
    return (itemPosition + base)
}