package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.Modifier

data class DrawShapeState<ShapeType : Shape<Id>, Id>(
    val modifier: Modifier,
    val shape: ShapeType,
    val isSelected: Boolean,
    val isDoubleClicked: Boolean,
    val index: Int
)