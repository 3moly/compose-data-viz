package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.geometry.Offset

data class DragAction<Id>(
    val startMapPosition: Offset,
    val accelerate: Offset,
    val dragType: DragType<Id>
)