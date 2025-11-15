package com.moly3.dataviz.block.model

sealed class DragType {
    data class Shape(
        val shapeId: Long
    ) : DragType()

    data class Connection(
        val startShapeId: Long,
        val startShapeType: BoxSide,
        val boxSide: com.moly3.dataviz.block.model.Shape
    ) : DragType()

    data class Resize(
        val shapeId: Long,
        val type: ResizeType,
        val boxSide: com.moly3.dataviz.block.model.Shape
    ) : DragType()
}