package com.moly3.dataviz.block.model

import androidx.compose.ui.geometry.Offset

sealed class Action {

    fun getOffsetMenu(shapes: List<com.moly3.dataviz.block.model.Shape>): Offset {
        return when (this) {
            is Connection -> selectedConnection.getMenuCenter()
            is DoubleClicked -> Offset(-1000f, -1000f)
            is Shape -> {
                val foundShape = shapes.firstOrNull { b -> b.id == shape.id } ?: shape

                foundShape.position + Offset(foundShape.size.x / 2f, 0f)
            }
        }
    }

    data class Connection(val selectedConnection: SelectedConnection) : Action()
    data class Shape(val shape: com.moly3.dataviz.block.model.Shape) : Action()

    data class DoubleClicked(val shape: com.moly3.dataviz.block.model.Shape) : Action()
}