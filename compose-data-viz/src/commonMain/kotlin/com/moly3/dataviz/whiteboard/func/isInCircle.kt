package com.moly3.dataviz.whiteboard.func

fun isInCircle(
    pointX: Float,
    pointY: Float,
    centerX: Float,
    centerY: Float,
    radius: Float
): Boolean {
    val dx = pointX - centerX
    val dy = pointY - centerY
    return dx * dx + dy * dy <= radius * radius
}