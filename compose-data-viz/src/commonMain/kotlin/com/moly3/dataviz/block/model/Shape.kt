package com.moly3.dataviz.block.model

import androidx.compose.ui.geometry.Offset

open class Shape(
    open val id: Long,
    open val position: WorldPosition,
    open val size: Offset,
)