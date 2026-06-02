package com.moly3.dataviz.core.graph.engine

import androidx.compose.ui.geometry.Offset

data class DragNodeData<Id>(
    val id: Id,
    val offset: Offset? = null,
)
