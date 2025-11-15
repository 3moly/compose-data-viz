package com.moly3.dataviz.func

import androidx.compose.ui.graphics.Color

fun Color.darker(factor: Float = 0.8f): Color {
    return Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha // preserve original alpha
    )
}