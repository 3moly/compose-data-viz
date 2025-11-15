package com.moly3.dataviz.block.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

// Data class to represent a connection between boxes
data class ArcConnection(
    val id: Long,
    val fromBox: Long,
    val toBox: Long,
    var fromSide: BoxSide = BoxSide.TOP,
    var toSide: BoxSide = BoxSide.TOP,
    val arcHeight: Float = 80f, // Height of the arc
    val color: Color?
)

@Serializable
data class ArcConnectionJson(
    val id: Long,
    val fromBox: Long,
    val toBox: Long,
    var fromSide: BoxSide = BoxSide.TOP,
    var toSide: BoxSide = BoxSide.TOP,
    val arcHeight: Float = 80f, // Height of the arc
    val color: String?
)