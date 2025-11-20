package com.threemoly.sample.block

sealed class ShapeData {
    data class Text(val text: String) : ShapeData()
    data class ImageUrl(val url: String) : ShapeData()
}