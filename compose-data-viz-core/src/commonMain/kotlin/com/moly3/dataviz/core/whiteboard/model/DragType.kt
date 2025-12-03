package com.moly3.dataviz.core.whiteboard.model

sealed class DragType<Id> {
    data class ShapeDrag<Id>(
        val shapeId: Id
    ) : DragType<Id>()

    data class Connection<Id>(
        val startShapeId: Id,
        val startShapeType: BoxSide,
        val boxSide: Shape<Id>
    ) : DragType<Id>()

    data class Resize<Id>(
        val shapeId: Id,
        val type: ResizeType
    ) : DragType<Id>()
}