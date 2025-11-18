package com.moly3.dataviz.block.func

import com.moly3.dataviz.block.model.ResizeType
import androidx.compose.ui.geometry.Offset

fun isInResizeArea(
    mousePositionX: Float,
    mousePositionY: Float,
    shapePositionX: Float,
    shapePositionY: Float,
    shapeSizeX: Float,
    shapeSizeY: Float,
    detectionPercent: Float //0.0f to 1.0f
): ResizeType? {
    val left = shapePositionX
    val top = shapePositionY
    val right = shapePositionX + shapeSizeX
    val bottom = shapePositionY + shapeSizeY

    // Calculate detection threshold (how many pixels from edge will trigger resize)
    val detectionThresholdX = shapeSizeX * detectionPercent
    val detectionThresholdY = shapeSizeY * detectionPercent

    // Check if mouse is inside shape at all
    val isInShape = mousePositionX in left..right && mousePositionY in top..bottom
    if (!isInShape) return null

    // Check which edges the mouse is near
    val isNearLeft = mousePositionX <= left + detectionThresholdX
    val isNearRight = mousePositionX >= right - detectionThresholdX
    val isNearTop = mousePositionY <= top + detectionThresholdY
    val isNearBottom = mousePositionY >= bottom - detectionThresholdY

    // Determine resize type based on position
    return when {
        isNearLeft && isNearTop -> ResizeType.TopLeft
        isNearRight && isNearTop -> ResizeType.TopRight
        isNearLeft && isNearBottom -> ResizeType.BottomLeft
        isNearRight && isNearBottom -> ResizeType.BottomRight
        isNearLeft -> ResizeType.Left
        isNearRight -> ResizeType.Right
        isNearTop -> ResizeType.Top
        isNearBottom -> ResizeType.Bottom
        else -> null // Mouse is inside shape but not near any edge
    }
}

fun isInResizeArea(
    mousePosition: Offset,
    shapePosition: Offset,
    shapeSize: Offset,
    detectionPercent: Float
): ResizeType? {
    return isInResizeArea(
        mousePositionX = mousePosition.x,
        mousePositionY = mousePosition.y,
        shapePositionX = shapePosition.x,
        shapePositionY = shapePosition.y,
        shapeSizeX = shapeSize.x,
        shapeSizeY = shapeSize.y,
        detectionPercent = detectionPercent
    )
}