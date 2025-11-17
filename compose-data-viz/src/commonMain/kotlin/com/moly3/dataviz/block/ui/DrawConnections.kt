package com.moly3.dataviz.block.ui

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
import com.moly3.dataviz.block.func.makeSideOffset
import com.moly3.dataviz.block.model.Action
import com.moly3.dataviz.block.model.ArcConnection
import com.moly3.dataviz.block.model.ConnectionConfig
import com.moly3.dataviz.block.model.DragAction
import com.moly3.dataviz.block.model.DragType
import com.moly3.dataviz.block.model.SaveableOffset
import com.moly3.dataviz.block.model.Shape
import com.moly3.dataviz.block.model.StylusPath
import com.moly3.dataviz.block.model.StylusPoint
import com.moly3.dataviz.block.model.reverse
import com.moly3.dataviz.func.drawSmoothArrow

@Composable
fun DrawConnections(
    modifier: Modifier,
    stylusPoint: List<StylusPoint>,
    paths: List<StylusPath>,
    userCoordinate: SaveableOffset,
    dragActionState: MutableState<DragAction?>,
    shapes: List<Shape>,
    connections: List<ArcConnection>,
    config: ConnectionConfig,
    zoom: Float,
    centerOfScreen: SaveableOffset,
    cursorPosition: SaveableOffset,
    action: Action?,
    selectedConnectionStrokeWidth: Float,
    lineColor: Color,
    drawColor: Color
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        for (connection in connections) {
            val fromBox = shapes.firstOrNull { b -> b.id == connection.fromBox }
            val toBox = shapes.firstOrNull { b -> b.id == connection.toBox }
            if (fromBox == null || toBox == null)
                continue
            val startPoint =
                makeSideOffset(
                    dragAction = dragActionState.value,
                    userCoordinate = userCoordinate,
                    boxSide = fromBox,
                    zoom = zoom,
                    side = connection.fromSide
                )
            val endPoint =
                makeSideOffset(
                    dragAction = dragActionState.value,
                    userCoordinate = userCoordinate,
                    boxSide = toBox,
                    zoom = zoom,
                    side = connection.toSide
                )
            drawSmoothArrow(
                id = connection.id,
                action = action,
                startPoint = (startPoint + centerOfScreen),
                endPoint = (endPoint + centerOfScreen),
                fromSide = connection.fromSide,
                toSide = connection.toSide,
                color = connection.color ?: lineColor,
                zoom = zoom,
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
                boxSize = connection.boxSide.size,
                zoom = zoom,
                side = connection.startShapeType
            )
            val endPoint = cursorPosition
            drawSmoothArrow(
                id = 1L,
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
    movementOffset: SaveableOffset,
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

fun DrawScope.drawCurrentStroke2(
    zoom: Float,
    movementOffset: Offset,
    points: List<StylusPoint>
) {
    withTransform({
        scale(zoom, zoom)
        translate(center.x + movementOffset.x, center.y + movementOffset.y)
    }) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            val point = points.first()
            drawCircle(
                color = Color.Red, // Use red to make sure it's visible
                radius = maxOf(point.strokeWidth / 2f, 8f), // Make it bigger
                center = Offset(point.x, point.y)
            )
            //println("Drew single point at ${point.x}, ${point.y}")
            return
        }

        // Draw all segments in the current path
        for (i in 1 until points.size) {
            val startPoint = points[i - 1]
            val endPoint = points[i]

            val avgStrokeWidth = maxOf((startPoint.strokeWidth + endPoint.strokeWidth) / 2f, 4f)

            drawLine(
                color = Color.Blue, // Use blue to make sure it's visible
                start = Offset(startPoint.x, startPoint.y),
                end = Offset(endPoint.x, endPoint.y),
                strokeWidth = avgStrokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Draw larger circles at each point for visibility
        points.forEach { point ->
            drawCircle(
                color = Color.Blue,
                radius = maxOf(point.strokeWidth / 2f, 4f),
                center = Offset(point.x, point.y)
            )
        }
    }
}