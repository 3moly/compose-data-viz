package com.moly3.dataviz.block.model

interface Shape {
    val id: Long
    val position: SaveableOffset
    val size: SaveableOffset
}