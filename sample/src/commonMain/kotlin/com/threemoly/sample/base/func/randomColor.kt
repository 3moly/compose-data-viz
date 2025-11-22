package com.threemoly.sample.base.func

import androidx.compose.ui.graphics.Color

fun randomColor(): Color {
    val colors = listOf(
        Color.Red,
        Color.Blue,
        Color.Green,
        Color.Yellow,
        Color.Magenta,
        Color.Cyan,
        Color.Gray,
        Color.DarkGray,
        Color(0xFF9C27B0), // Purple
        Color(0xFFFFC107)  // Amber
    )
    return colors.random()
}