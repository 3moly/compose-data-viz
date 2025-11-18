package com.moly3.dataviz.block.model

import androidx.compose.ui.geometry.Offset

data class DragAction(
    val startMapPosition: Offset,
    val accelerate: Offset,
    val dragType: DragType
)