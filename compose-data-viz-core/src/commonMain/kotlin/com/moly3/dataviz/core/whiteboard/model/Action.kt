package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.geometry.Offset

sealed class Action<ShapeType : Shape<Id>, Id> {

    fun <ShapeType : Shape<Id>> getOffsetMenu(shapes: List<Shape<Id>>): Offset {
        return when (this) {
            is Connection -> selectedConnection.getMenuCenter()
            is DoubleClicked -> Offset(-1000f, -1000f)
            is ShapeAction -> {
                val foundShape = shapes.lastOrNull { b -> b.id == shape.id } ?: shape

                foundShape.position + Offset(foundShape.size.x / 2f, 0f)
            }
        }
    }

    data class Connection<ShapeType : Shape<Id>, Id>(val selectedConnection: SelectedConnection<Id>) :
        Action<ShapeType, Id>()

    data class ShapeAction<ShapeType : Shape<Id>, Id>(val shape: ShapeType) :
        Action<ShapeType, Id>()

    data class DoubleClicked<ShapeType : Shape<Id>, Id>(val shape: ShapeType) :
        Action<ShapeType, Id>()
}