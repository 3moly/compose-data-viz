package com.moly3.dataviz.core.whiteboard.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.moly3.dataviz.core.whiteboard.model.StylusPath

data class PathBounds(
    val size: Size,
    val localCenter: Offset,
    val globalPosition: Offset
)

fun StylusPath.calculateBounds(): PathBounds {
    if (points.isEmpty()) {
        return PathBounds(Size.Zero, Offset.Zero, Offset.Zero)
    }

    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE // Fix: Use negative MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE // Fix: Use negative MAX_VALUE

    for (point in points) {
        val strokeRadius = point.strokeWidth / 2f

        val leftEdge = point.x - strokeRadius
        val rightEdge = point.x + strokeRadius
        val topEdge = point.y - strokeRadius
        val bottomEdge = point.y + strokeRadius

        if (leftEdge < minX) minX = leftEdge
        if (rightEdge > maxX) maxX = rightEdge
        if (topEdge < minY) minY = topEdge
        if (bottomEdge > maxY) maxY = bottomEdge
    }

    val width = maxX - minX
    val height = maxY - minY

    val size = Size(width, height)
    val localCenter = Offset(width / 2f, height / 2f)
    val globalPosition = Offset(minX, minY)

    return PathBounds(size, localCenter, globalPosition)
}