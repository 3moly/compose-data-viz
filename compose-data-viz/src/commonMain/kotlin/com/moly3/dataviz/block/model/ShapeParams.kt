package com.moly3.dataviz.block.model

import androidx.compose.ui.unit.Dp

data class ShapeParams(
    val itemPosition: WorldPosition,
    val offset: Coords,
    val itemWidth: Dp,
    val itemHeight: Dp,
)