package com.moly3.dataviz.core.block.model

import androidx.compose.ui.geometry.Offset

interface Shape {
    val id: Long
    val position: Offset
    val size: Offset
}