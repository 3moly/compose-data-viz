package com.threemoly.sample.base.block

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.moly3.dataviz.core.whiteboard.model.Shape

data class CustomShape(
    override val id: Long,
    override val position: Offset,
    override val size: Offset,

    val backgroundColor: Color?,
    val data: ShapeData
) : Shape<Long>