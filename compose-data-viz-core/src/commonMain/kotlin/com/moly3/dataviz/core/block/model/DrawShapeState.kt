package com.moly3.dataviz.core.block.model

import androidx.compose.ui.Modifier

data class DrawShapeState<ShapeType : Shape>(
    val modifier: Modifier,
    val shape: ShapeType,
    val isSelected: Boolean,
    val isDoubleClicked: Boolean
)