package com.moly3.dataviz.block.model


data class DragAction(
    val startMapPosition: WorldPosition,
    val accelerate: WorldPosition,
    val dragType: DragType
)