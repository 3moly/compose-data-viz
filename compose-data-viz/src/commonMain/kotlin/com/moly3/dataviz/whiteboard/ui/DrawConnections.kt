package com.moly3.dataviz.whiteboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.moly3.dataviz.whiteboard.func.makeSideOffset
import com.moly3.dataviz.whiteboard.func.reverse
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.ShapeConnection
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.StylusPath
import com.moly3.dataviz.core.whiteboard.model.StylusPoint
import com.moly3.dataviz.func.drawSmoothArrow

@Composable
fun <ShapeType : Shape<Id>, Id> DrawConnections(
    modifier: Modifier,
    connectionDragBlankId: Id,
    zoom: Float,
    centerOfScreen: Offset,
    cursorPosition: Offset,
    userCoordinate: Offset,
    selectedConnectionStrokeWidth: Float,
    lineColor: Color,
    drawColor: Color,
    roundToNearest: Int?,
    shapes: List<ShapeType>,
    connections: List<ShapeConnection<Id>>,
    stylusPoint: List<StylusPoint>,
    paths: List<StylusPath>,
    dragActionState: MutableState<DragAction<Id>?>,
    config: ConnectionConfig,
    action: Action<ShapeType, Id>?,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        withTransform({

        }){
            for (connection in connections) {
                val fromBox = shapes.lastOrNull { b -> b.id == connection.fromBoxId }
                val toBox = shapes.lastOrNull { b -> b.id == connection.toBoxId }
                if (fromBox == null || toBox == null)
                    continue
                val startPoint =
                    makeSideOffset(
                        dragAction = dragActionState.value,
                        userCoordinate = userCoordinate,
                        boxSide = fromBox,
                        zoom = zoom,
                        side = connection.fromSide,
                        roundToNearest = roundToNearest,
                    )
                val endPoint =
                    makeSideOffset(
                        dragAction = dragActionState.value,
                        userCoordinate = userCoordinate,
                        boxSide = toBox,
                        zoom = zoom,
                        side = connection.toSide,
                        roundToNearest = roundToNearest
                    )
                drawSmoothArrow(
                    id = connection.id,
                    action = action,
                    startPoint = (startPoint + centerOfScreen),
                    endPoint = (endPoint + centerOfScreen),
                    fromSide = connection.fromSide,
                    toSide = connection.toSide,
                    color = connection.color ?: lineColor,
                    zoom = zoom /density,
                    config = config,
                    selectedConnectionStrokeWidth = selectedConnectionStrokeWidth
                )
            }
            if (dragActionState.value != null && dragActionState.value!!.dragType is DragType.Connection) {
                val connection =
                    (dragActionState.value!!.dragType as DragType.Connection)

                val startPoint = makeSideOffset(
                    itemPosition = connection.boxSide.position,
                    userCoordinate = userCoordinate,
                    shapeSize = connection.boxSide.size,
                    zoom = zoom,
                    side = connection.startShapeType
                )
                val endPoint = cursorPosition
                drawSmoothArrow(
                    id = connectionDragBlankId,
                    startPoint = (startPoint + centerOfScreen),
                    endPoint = (endPoint),
                    fromSide = connection.startShapeType,
                    toSide = connection.startShapeType.reverse(),
                    color = lineColor,
                    zoom = zoom,
                    config = config,
                    action = null
                )
            }
        }

        for (path in paths) {
            drawCompletedPath(
                zoom = zoom,
                movementOffset = -userCoordinate,
                path = path
            )
        }
        drawCompletedPath(
            zoom = zoom,
            movementOffset = -userCoordinate,
            StylusPath(
                points = stylusPoint,
                color = drawColor
            )
        )
    }
}

fun DrawScope.drawCompletedPath(
    zoom: Float,
    movementOffset: Offset,
    path: StylusPath
) {
    withTransform({
        scale(zoom, zoom)
        translate(center.x + movementOffset.x, center.y + movementOffset.y)
    }) {
        if (path.points.isEmpty()) return

        // Draw single point
        if (path.points.size == 1) {
            val point = path.points.first()
            drawCircle(
                color = path.color,
                radius = point.strokeWidth / 2f,
                center = Offset(point.x, point.y)
            )
            return
        }

        // Draw each segment with its individual stroke width
        for (i in 1 until path.points.size) {
            val startPoint = path.points[i - 1]
            val endPoint = path.points[i]

            val avgStrokeWidth = (startPoint.strokeWidth + endPoint.strokeWidth) / 2f

            drawLine(
                color = path.color,
                start = Offset(startPoint.x, startPoint.y),
                end = Offset(endPoint.x, endPoint.y),
                strokeWidth = avgStrokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Draw circles at each point for smoother connections
        path.points.forEach { point ->
            drawCircle(
                color = path.color,
                radius = point.strokeWidth / 2f,
                center = Offset(point.x, point.y)
            )
        }
    }
}