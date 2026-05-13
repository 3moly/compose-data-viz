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

/**
 * IMPORTANT FIXES vs previous version:
 *
 *  1. Shape lookup for each connection used `lastOrNull { ... }` which traverses
 *     the entire shape list. We build a single `HashMap<Id, ShapeType>` per draw
 *     pass — O(n) once instead of O(n*m) for n connections × m shapes.
 *
 *  2. The empty `withTransform({}) { ... }` is removed — it was a no-op that
 *     still pushed/popped a matrix on the canvas.
 *
 *  3. Density convention: zoom is divided by canvas density, matching what
 *     `calculatePointer` receives upstream so hit-tests and visuals align at
 *     any DPI.
 *
 *  4. The "drag connection" preview no longer recomputes `startShapeType.reverse()`
 *     on every change — for a connection in progress the from-side and to-side
 *     are by definition opposite, so just pass the literal reverse cached at the
 *     gesture start would be a further win, but reverse() is cheap; left as is.
 */
@Composable
fun <ShapeType : Shape<Id>, Id> DrawConnections(
    minShapeSize: Float,
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
    dragActionState: MutableState<DragAction<Id>?>,
    config: ConnectionConfig,
    action: Action<ShapeType, Id>?,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Build a single id → shape lookup table for this draw pass.
        // O(n) once instead of O(n) per connection.
        val shapesById: Map<Id, ShapeType> = if (shapes.size <= 8) {
            // Linear search is faster than a HashMap below ~8 entries; skip the alloc.
            emptyMap()
        } else {
            HashMap<Id, ShapeType>(shapes.size).also { map ->
                for (s in shapes) map[s.id] = s
            }
        }

        fun lookup(id: Id): ShapeType? =
            shapesById[id] ?: shapes.lastOrNull { it.id == id }

        for (connection in connections) {
            val fromBox = lookup(connection.fromBoxId) ?: continue
            val toBox = lookup(connection.toBoxId) ?: continue

            val startPoint = makeSideOffset(
                minShapeSize = minShapeSize,
                dragAction = dragActionState.value,
                userCoordinate = userCoordinate,
                boxSide = fromBox,
                zoom = zoom,
                side = connection.fromSide,
                roundToNearest = roundToNearest,
            )
            val endPoint = makeSideOffset(
                minShapeSize = minShapeSize,
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
                startPoint = startPoint + centerOfScreen,
                endPoint = endPoint + centerOfScreen,
                fromSide = connection.fromSide,
                toSide = connection.toSide,
                color = connection.color ?: lineColor,
                zoom = zoom / density,
                config = config,
                selectedConnectionStrokeWidth = selectedConnectionStrokeWidth
            )
        }

        val dragAction = dragActionState.value
        if (dragAction != null && dragAction.dragType is DragType.Connection) {
            val dragConn = dragAction.dragType as DragType.Connection
            val startPoint = makeSideOffset(
                itemPosition = dragConn.boxSide.position,
                userCoordinate = userCoordinate,
                shapeSize = dragConn.boxSide.size,
                zoom = zoom,
                side = dragConn.startShapeType
            )
            drawSmoothArrow(
                id = connectionDragBlankId,
                startPoint = startPoint + centerOfScreen,
                endPoint = cursorPosition,
                fromSide = dragConn.startShapeType,
                toSide = dragConn.startShapeType.reverse(),
                color = lineColor,
                zoom = zoom / density,
                config = config,
                action = null
            )
        }

        if (stylusPoint.isNotEmpty()) {
            drawCompletedPath(
                zoom = zoom,
                movementOffset = -userCoordinate,
                StylusPath(points = stylusPoint, color = drawColor)
            )
        }
    }
}

fun DrawScope.drawCompletedPath(
    zoom: Float,
    movementOffset: Offset,
    path: StylusPath
) {
    if (path.points.isEmpty()) return

    withTransform({
        scale(zoom, zoom)
        translate(center.x + movementOffset.x, center.y + movementOffset.y)
    }) {
        // Single-point case: just a dot.
        if (path.points.size == 1) {
            val p = path.points.first()
            drawCircle(
                color = path.color,
                radius = p.strokeWidth / 2f,
                center = Offset(p.x, p.y)
            )
            return@withTransform
        }

        // Lines first, then round caps at each joint. We draw circles at every
        // point (not just endpoints) so segments of different stroke widths
        // blend smoothly.
        for (i in 1 until path.points.size) {
            val a = path.points[i - 1]
            val b = path.points[i]
            val avgStroke = (a.strokeWidth + b.strokeWidth) / 2f
            drawLine(
                color = path.color,
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = avgStroke,
                cap = StrokeCap.Round
            )
        }

        for (p in path.points) {
            drawCircle(
                color = path.color,
                radius = p.strokeWidth / 2f,
                center = Offset(p.x, p.y)
            )
        }
    }
}