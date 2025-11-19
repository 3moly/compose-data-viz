package com.moly3.dataviz.core.block.model

sealed class DragType {
    data class ShapeDrag(
        val shapeId: Long
    ) : DragType()

    data class Connection(
        val startShapeId: Long,
        val startShapeType: BoxSide,
        val boxSide: Shape
    ) : DragType()

    data class Resize(
        val shapeId: Long,
        val type: ResizeType,
        val boxSide: Shape
    ) : DragType()
}