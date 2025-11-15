package com.moly3.dataviz.block.model

import androidx.compose.ui.graphics.Color
import kotlin.time.ExperimentalTime

data class StylusPoint @OptIn(ExperimentalTime::class) constructor(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val strokeWidth: Float = 5f,
    val timestamp: Long
)

data class StylusPath(
    val points: List<StylusPoint>,
    val color: Color
)