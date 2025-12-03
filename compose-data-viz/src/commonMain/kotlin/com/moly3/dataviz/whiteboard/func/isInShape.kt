package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.whiteboard.model.ResizeType

fun isInShape(
    mousePosition: Offset,
    shapePosition: Offset,
    shapeSize: Offset
): Boolean {
    val left = shapePosition.x
    val top = shapePosition.y
    val right = shapePosition.x + shapeSize.x
    val bottom = shapePosition.y + shapeSize.y
    val mouseX = mousePosition.x
    val mouseY = mousePosition.y

    return mouseX in left..right && mouseY in top..bottom
}

fun isInShapeComplex(
    mousePosition: Offset,
    shapePosition: Offset,
    shapeSize: Offset,
    detectionPercent: Float = 0.1f,
    circleRadius: Float? = null
): InShape? {
    val left = shapePosition.x
    val top = shapePosition.y
    val right = shapePosition.x + shapeSize.x
    val bottom = shapePosition.y + shapeSize.y
    val mouseX = mousePosition.x
    val mouseY = mousePosition.y

    // Check corner circles first (they have priority over body)
    val topLeft = Offset(left, top)
    val topRight = Offset(right, top)
    val bottomLeft = Offset(left, bottom)
    val bottomRight = Offset(right, bottom)

    // Check if mouse is inside any corner circle
    if (circleRadius != null) {
        if (isInsideCircle(mousePosition, topLeft, circleRadius)) {
            return InShape.Resize(ResizeType.TopLeft)
        }
        if (isInsideCircle(mousePosition, topRight, circleRadius)) {
            return InShape.Resize(ResizeType.TopRight)
        }
        if (isInsideCircle(mousePosition, bottomLeft, circleRadius)) {
            return InShape.Resize(ResizeType.BottomLeft)
        }
        if (isInsideCircle(mousePosition, bottomRight, circleRadius)) {
            return InShape.Resize(ResizeType.BottomRight)
        }
    }

    val detectionThresholdX = shapeSize.x * detectionPercent
    val detectionThresholdY = shapeSize.y * detectionPercent

    return if (mouseX in left..right && mouseY in top..bottom) {
        when {
            mouseX <= left + detectionThresholdX -> InShape.Resize(ResizeType.Left)
            mouseX >= right - detectionThresholdX -> InShape.Resize(ResizeType.Right)
            mouseY <= top + detectionThresholdY -> InShape.Resize(ResizeType.Top)
            mouseY >= bottom - detectionThresholdY -> InShape.Resize(ResizeType.Bottom)
            else -> InShape.Body
        }
    } else {
        null
    }
}

private fun isInsideCircle(point: Offset, center: Offset, radius: Float): Boolean {
    val dx = point.x - center.x
    val dy = point.y - center.y
    return (dx * dx + dy * dy) <= (radius * radius)
}

sealed class InShape {
    data object Body : InShape()
    data class Resize(val resizeType: ResizeType) : InShape()
}