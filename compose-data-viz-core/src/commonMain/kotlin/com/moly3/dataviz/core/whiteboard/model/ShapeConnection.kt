package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.graphics.Color

data class ShapeConnection<Id>(
    val id: Id,
    val fromBoxId: Id,
    val toBoxId: Id,
    val fromSide: BoxSide,
    val toSide: BoxSide,
    val arcHeight: Float = 80f,
    val color: Color?
)