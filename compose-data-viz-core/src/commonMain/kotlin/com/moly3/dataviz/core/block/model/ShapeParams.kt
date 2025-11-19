package com.moly3.dataviz.core.block.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

data class ShapeParams(
    val itemPosition: Offset,
    val offset: Coords,
    val itemWidth: Dp,
    val itemHeight: Dp,
)