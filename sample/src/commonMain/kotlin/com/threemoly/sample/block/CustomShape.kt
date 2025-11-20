package com.threemoly.sample.block

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.moly3.dataviz.core.block.model.Shape

data class CustomShape(
    override val id: Long,
    val color: Color?,
    override val position: Offset,
    override val size: Offset,
    val data: ShapeData
) : Shape