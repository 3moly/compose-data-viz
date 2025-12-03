package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.func.lastNotNullOfOrNull

fun <ShapeType : Shape<Id>, Id> getShapeGlobalDragType(
    mousePosition: Offset,
    shapes: List<ShapeType>,
    action: Action<ShapeType, Id>?,
    sizeRound: Int,
    circleRadius: Float?
): Pair<DragType<Id>, ShapeType>? {
    val shapeDragType = (action as? Action.ShapeAction)
    val targetShape = if (shapeDragType != null) {
        shapes.firstOrNull { b -> b.id == shapeDragType.shape.id }
    } else {
        null
    }
    return if (targetShape != null) {
        val sideShape = getSideShapeDragAction(
            mousePosition = mousePosition,
            sizeRound = sizeRound,
            shape = targetShape
        )
        if (sideShape != null) {
            Pair(sideShape, targetShape)
        } else {

            val inShape = isInShapeComplex(
                mousePosition,
                targetShape.position,
                targetShape.size,
                circleRadius = circleRadius
            )

            if (inShape != null) {
                when (inShape) {
                    InShape.Body -> {
                        Pair(
                            DragType.ShapeDrag(
                                shapeId = targetShape.id
                            ), targetShape
                        )
                    }

                    is InShape.Resize -> {
                        Pair(
                            DragType.Resize(
                                shapeId = targetShape.id,
                                type = inShape.resizeType
                            ), targetShape
                        )
                    }
                }
            } else
                null
        }
    } else {
        shapes.lastNotNullOfOrNull { shape ->
            val inShape = isInShapeComplex(
                mousePosition,
                shape.position,
                shape.size,
                circleRadius = circleRadius
            )
            if (inShape != null) {
                Pair(
                    DragType.ShapeDrag(
                        shapeId = shape.id
                    ), shape
                )
            } else
                null
        }
    }
}