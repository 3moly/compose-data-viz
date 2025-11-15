package com.moly3.dataviz.graph.func

import androidx.compose.ui.geometry.Offset

fun isNodeTapped(
    nodeOffset: Offset,
    cameraOffset: Offset,
    tapOffset: Offset,
    nodeRadius: Float //100%
): Boolean {
    val adjustedTapPosition = tapOffset + cameraOffset
    val distance = (adjustedTapPosition - nodeOffset).getDistance()
    return distance <= nodeRadius
}