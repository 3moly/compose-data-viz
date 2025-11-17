package com.moly3.dataviz.block.model


data class DragAction(
    val startMapPosition: SaveableOffset,
    val accelerate: SaveableOffset,
    val dragType: DragType
)