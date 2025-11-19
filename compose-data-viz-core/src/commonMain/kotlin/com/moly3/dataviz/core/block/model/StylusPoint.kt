package com.moly3.dataviz.core.block.model

data class StylusPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val strokeWidth: Float = 5f,
    val timestamp: Long
)