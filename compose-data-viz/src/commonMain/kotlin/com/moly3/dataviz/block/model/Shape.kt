package com.moly3.dataviz.block.model

import androidx.compose.ui.geometry.Offset

interface Shape {
    val id: Long
    val position: Offset
    val size: Offset
}