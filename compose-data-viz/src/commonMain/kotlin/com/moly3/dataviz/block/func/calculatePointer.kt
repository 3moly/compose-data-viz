package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import com.moly3.dataviz.block.model.ArcConnection
import com.moly3.dataviz.block.model.BoxSide
import com.moly3.dataviz.block.model.ConnectionConfig
import com.moly3.dataviz.block.model.DetectionType
import com.moly3.dataviz.block.model.DragAction
import com.moly3.dataviz.block.model.Shape
import com.moly3.dataviz.block.model.PointerDetection
import com.moly3.dataviz.block.model.PointerIconType
import com.moly3.dataviz.block.model.ResizeType
import com.moly3.dataviz.block.model.WorldPosition
import com.moly3.dataviz.block.model.allSides

fun calculatePointer(
    shapes: List<Shape>,
    mapCursor: WorldPosition,
    connections: List<ArcConnection>,
    zoom: Float,
    connectionConfig: ConnectionConfig,
    userCoordinate: Offset,
    centerOfScreen: Offset,
    cursorPosition: Offset,
    dragAction: DragAction?,
    sizeRound: Int
): PointerDetection {
    val foundShape =
        shapes.firstOrNull { x ->
            isInShape(
                mousePosition = mapCursor,
                shapePosition = x.position,
                shapeSize = x.size
            )
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
        config = connectionConfig
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

    return if (foundSideShape != null || foundShape != null || foundConnection != null) {

        if (foundSideShape != null)
            PointerDetection(PointerIcon.Hand, DetectionType.SideBox)
        else if (foundResize != null) {
            val pointerType = when (foundResize.second) {
                ResizeType.TopLeft -> PointerIconType.ResizeTopLeft
                ResizeType.TopRight -> PointerIconType.ResizeTopRight
                ResizeType.BottomLeft -> PointerIconType.ResizeBottomLeft
                ResizeType.BottomRight -> PointerIconType.ResizeBottomRight
                ResizeType.Bottom -> PointerIconType.ResizeVertical
                ResizeType.Right -> PointerIconType.ResizeHorizontal
                ResizeType.Top -> PointerIconType.ResizeVertical
                ResizeType.Left -> PointerIconType.ResizeHorizontal
            }
            PointerDetection(getPointerIcon(pointerType), DetectionType.ResizeBox)
        } else if (foundShape != null)
            PointerDetection(PointerIcon.Hand, DetectionType.Box)
        else if (foundConnection != null)
            PointerDetection(PointerIcon.Hand, DetectionType.Arrow)
        else PointerDetection(PointerIcon.Default, null)
    } else {
        PointerDetection(PointerIcon.Default, null)
    }
}