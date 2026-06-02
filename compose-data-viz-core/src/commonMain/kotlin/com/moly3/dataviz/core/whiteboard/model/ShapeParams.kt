package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.geometry.Offset

data class ShapeParams(
    val itemPosition: Offset,
    val offset: Coords,
    val size: Offset,
)
