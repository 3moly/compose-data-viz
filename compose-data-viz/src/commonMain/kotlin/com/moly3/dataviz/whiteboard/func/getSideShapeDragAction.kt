package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.allSides

fun <Id> getSideShapeDragAction(
    mousePosition: Offset,
    sizeRound: Int,
    shape: Shape<Id>
): DragType.Connection<Id>? {
    for (side in allSides) {
        val isInSide = isInSidePosition(
            mousePosition = mousePosition,
            itemPosition = shape.position,
            boxSize = shape.size,
            side = side,
            radius = sizeRound / 2f
        )
        if (isInSide) {
            return DragType.Connection(
                startShapeId = shape.id,
                startShapeType = side,
                boxSide = shape
            )
        }
    }
    return null
}