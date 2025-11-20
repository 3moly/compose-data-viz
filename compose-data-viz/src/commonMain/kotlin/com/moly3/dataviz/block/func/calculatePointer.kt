package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import com.moly3.dataviz.core.block.model.ShapeConnection
import com.moly3.dataviz.core.block.model.BoxSide
import com.moly3.dataviz.core.block.model.ConnectionConfig
import com.moly3.dataviz.core.block.model.DetectionType
import com.moly3.dataviz.core.block.model.DragAction
import com.moly3.dataviz.core.block.model.Shape
import com.moly3.dataviz.core.block.model.PointerDetection
import com.moly3.dataviz.core.block.model.PointerIconType
import com.moly3.dataviz.core.block.model.ResizeType
import com.moly3.dataviz.core.block.model.allSides

fun calculatePointer(
    shapes: List<Shape>,
    mapCursor: Offset,
    connections: List<ShapeConnection>,
    zoom: Float,
    connectionConfig: ConnectionConfig,
    userCoordinate: Offset,
    centerOfScreen: Offset,
    cursorPosition: Offset,
    dragAction: DragAction?,
    sizeRound: Int,
    detectionPercent: Float,
    circleRadius: Float?,
    roundToNearest: Int?
): PointerDetection {
    val foundShape =
        shapes.firstNotNullOfOrNull { x ->
            val found = isInShapeComplex(
                mousePosition = mapCursor,
                shapePosition = x.position,
                shapeSize = x.size,
                detectionPercent = detectionPercent,
                circleRadius = circleRadius
            )
            if (found != null) {
                Pair(x, found)
            } else
                null
        }
    if (foundShape != null) {
        return when (val inShape = foundShape.second) {
            InShape.Body -> PointerDetection(PointerIcon.Hand, DetectionType.Box)
            is InShape.Resize -> {
                PointerDetection(
                    getPointerIcon(inShape.resizeType.toPointerType()),
                    DetectionType.Box
                )
            }
        }
    }


    var foundResize: Pair<Shape, ResizeType>? = null
    for (shape in shapes) {
        val resizeType = isInResizeArea(
            mousePosition = mapCursor,
            shapePosition = shape.position,
            shapeSize = shape.size,
            detectionPercent = 0.1f
        )
        if (resizeType != null) {
            foundResize = Pair(shape, resizeType)
        }
    }

    val foundConnection = findConnection(
        shapes = shapes,
        connections = connections,
        dragAction = dragAction,
        cursorPosition = cursorPosition,
        centerOfScreen = centerOfScreen,
        userCoordinate = userCoordinate,
        zoom = zoom,
        config = connectionConfig,
        roundToNearest = roundToNearest
    )
    var foundSideShape: Pair<Shape, BoxSide>? = null
    for (shape in shapes) {
        for (side in allSides) {
            val isInSide = isInSidePosition(
                mousePosition = mapCursor,
                itemPosition = shape.position,
                boxSize = shape.size,
                side = side,
                radius = sizeRound / 2f
            )
            if (isInSide) {
                foundSideShape = Pair(shape, side)
                break
            }
        }
    }

    return if (foundSideShape != null || foundConnection != null) {

        if (foundSideShape != null)
            PointerDetection(PointerIcon.Hand, DetectionType.SideBox)
        else if (foundResize != null) {
            PointerDetection(
                getPointerIcon(foundResize.second.toPointerType()),
                DetectionType.ResizeBox
            )
        } else if (foundConnection != null)
            PointerDetection(PointerIcon.Hand, DetectionType.Arrow)
        else PointerDetection(PointerIcon.Default, null)
    } else {
        PointerDetection(PointerIcon.Default, null)
    }
}

fun ResizeType.toPointerType(): PointerIconType {
    return when (this) {
        ResizeType.TopLeft -> PointerIconType.ResizeTopLeft
        ResizeType.TopRight -> PointerIconType.ResizeTopRight
        ResizeType.BottomLeft -> PointerIconType.ResizeBottomLeft
        ResizeType.BottomRight -> PointerIconType.ResizeBottomRight
        ResizeType.Bottom -> PointerIconType.ResizeVertical
        ResizeType.Right -> PointerIconType.ResizeHorizontal
        ResizeType.Top -> PointerIconType.ResizeVertical
        ResizeType.Left -> PointerIconType.ResizeHorizontal
    }
}