package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.graphics.Color

data class CanvasSettings(
    val strokeWidth: Float = 1f,
    val defaultLineColor: Color,
    val sideCircleColor: Color,
    val hitThreshold: Float = 15f,
    val stubLength: Float = 1f,
    val controlPointer: Float = 1f,
    val maxHit: Float = 100f
)