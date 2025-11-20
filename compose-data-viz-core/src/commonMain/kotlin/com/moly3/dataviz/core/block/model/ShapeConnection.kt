package com.moly3.dataviz.core.block.model

import androidx.compose.ui.graphics.Color

data class ShapeConnection(
    val id: Long,
    val fromBox: Long,
    val toBox: Long,
    var fromSide: BoxSide,
    var toSide: BoxSide,
    val arcHeight: Float = 80f,
    val color: Color?
)