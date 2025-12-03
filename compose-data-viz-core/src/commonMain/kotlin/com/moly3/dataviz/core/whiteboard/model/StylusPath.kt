package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.graphics.Color

data class StylusPath(
    val points: List<StylusPoint>,
    val color: Color
)