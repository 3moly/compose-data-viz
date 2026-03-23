package com.threemoly.sample.base.block

import com.moly3.dataviz.core.whiteboard.model.StylusPath

sealed class ShapeData {
    data class Text(val text: String) : ShapeData()
    data class ImageUrl(val url: String) : ShapeData()
    data class Drawing(val value: StylusPath) : ShapeData()
}