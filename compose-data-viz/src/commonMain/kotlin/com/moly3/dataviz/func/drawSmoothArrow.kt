package com.moly3.dataviz.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.BoxSide
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.core.whiteboard.model.Shape
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

fun <ShapeType : Shape<Id>, Id> DrawScope.drawSmoothArrow(
    id: Id,
    startPoint: Offset,
    endPoint: Offset,
    fromSide: BoxSide,
    toSide: BoxSide,
    color: Color,
    config: ConnectionConfig,
    zoom: Float = 1f,
    action: Action<ShapeType, Id>?,
    selectedConnectionStrokeWidth: Float = 1f
) {
    val strokeWidth = config.strokeWidth.toPx()
    val arrowHeadSize = config.arrowHeadSize.toPx()

    val stubLength = config.stubLength
    val maxArcHeight = config.maxArcHeight
    val controlPointFactor = config.controlPointFactor

    // Save original input parameters as requested
    val originalStartPoint = startPoint
    val originalEndPoint = endPoint

    // Apply zoom scaling to size parameters
    val scaledStrokeWidth = strokeWidth * zoom
    val scaledStubLength = stubLength * zoom
    val scaledArrowHeadSize = arrowHeadSize * zoom
    val scaledMaxArcHeight = maxArcHeight * zoom

    // 1. Calculate stub start/end points and curve start/end points
    val (stubStartPoint, curveStartPoint) = calculateStubAndCurvePoints(
        startPoint,
        fromSide,
        scaledStubLength
    )
    val (stubEndPoint, curveEndPoint) = calculateStubAndCurvePoints(
        endPoint,
        toSide,
        scaledStubLength
    )

    // Avoid drawing if points are identical or too close after stub calculation
    if (((curveStartPoint - curveEndPoint)).getDistanceSquared() < 0.1f) return

    // 3. Create the path
    val path = Path().apply {
        moveTo(stubStartPoint.x, stubStartPoint.y)
        lineTo(curveStartPoint.x, curveStartPoint.y)

        val controlPoints = calculateControlPoints(
            curveStartPoint,
            curveEndPoint,
            fromSide,
            toSide,
            controlPointFactor,
            originalStartPoint, // Pass original start point
            originalEndPoint,   // Pass original end point
            scaledMaxArcHeight  // Pass the scaled maximum arc height
        )
        cubicTo(
            controlPoints.first.x,
            controlPoints.first.y,
            controlPoints.second.x,
            controlPoints.second.y,
            curveEndPoint.x,
            curveEndPoint.y
        )
        lineTo(stubEndPoint.x, stubEndPoint.y)
    }

    // 4. Draw the path
    if (action is Action.Connection) {
        if (action.selectedConnection.connection.id == id) {
            drawPath(
                path = path,
                color = color.copy(alpha = 0.4f),
                style = Stroke(width = scaledStrokeWidth * selectedConnectionStrokeWidth)
            )
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = scaledStrokeWidth)
    )

    // Draw the arrowhead
    drawArrowHead(
        tip = stubEndPoint,
        from = curveEndPoint, // Use curveEndPoint directly for cleaner arrow direction
        color = color,
        strokeWidth = scaledStrokeWidth,
        arrowHeadSize = scaledArrowHeadSize
    )
}


// Helper to calculate points based on side and stub length
// Revised to ensure arrows always point outward from shapes
fun calculateStubAndCurvePoints(
    point: Offset,
    side: BoxSide,
    stubLength: Float
): Pair<Offset, Offset> {
    val stubPoint = point // The point on the "box" edge

    // Always direct stubs outward from the shape
    // For start points and end points, we ensure consistent outward direction
    val curvePoint = when (side) {
        BoxSide.TOP -> point.copy(y = point.y - stubLength) // Always upward (outward from top edge)
        BoxSide.BOTTOM -> point.copy(y = point.y + stubLength) // Always downward (outward from bottom edge)
        BoxSide.LEFT -> point.copy(x = point.x - stubLength) // Always leftward (outward from left edge)
        BoxSide.RIGHT -> point.copy(x = point.x + stubLength) // Always rightward (outward from right edge)
    }
    return Pair(stubPoint, curvePoint)
}

// Helper to calculate Bézier control points
fun calculateControlPoints(
    curveStart: Offset,
    curveEnd: Offset,
    fromSide: BoxSide,
    toSide: BoxSide,
    factor: Float,
    // Add original points for symmetric distance calculation
    originalStartPoint: Offset,
    originalEndPoint: Offset,
    maxArcHeight: Float = 100f // Maximum arc height in pixels
): Pair<Offset, Offset> {
    // --- SYMMETRY FIX ---
    // Calculate distance based on the *original* points for consistency
    val dxOriginal = originalEndPoint.x - originalStartPoint.x
    val dyOriginal = originalEndPoint.y - originalStartPoint.y
    val originalDistance = sqrt(dxOriginal * dxOriginal + dyOriginal * dyOriginal)
    // Avoid division by zero or tiny lengths if points are coincident
    var controlLength = if (originalDistance < 0.1f) 0f else originalDistance * factor
    // --- END SYMMETRY FIX ---
    controlLength = min(controlLength, maxArcHeight)
    // Control point 1 extends from curveStart in the direction of the stub (outward from the box)
    val control1 = when (fromSide) {
        BoxSide.TOP -> curveStart.copy(y = curveStart.y - controlLength) // Continue upward
        BoxSide.BOTTOM -> curveStart.copy(y = curveStart.y + controlLength) // Continue downward
        BoxSide.LEFT -> curveStart.copy(x = curveStart.x - controlLength) // Continue leftward
        BoxSide.RIGHT -> curveStart.copy(x = curveStart.x + controlLength) // Continue rightward
    }

    // Control point 2 extends from curveEnd approaching from the direction outside the box
    val control2 = when (toSide) {
        BoxSide.TOP -> curveEnd.copy(y = curveEnd.y - controlLength) // Approach from above
        BoxSide.BOTTOM -> curveEnd.copy(y = curveEnd.y + controlLength) // Approach from below
        BoxSide.LEFT -> curveEnd.copy(x = curveEnd.x - controlLength) // Approach from left
        BoxSide.RIGHT -> curveEnd.copy(x = curveEnd.x + controlLength) // Approach from right
    }

    return Pair(control1, control2)
}

// Helper function to draw the arrowhead
private fun DrawScope.drawArrowHead(
    tip: Offset,
    from: Offset,
    color: Color,
    strokeWidth: Float,
    arrowHeadSize: Float
) {
    val deltaX = tip.x - from.x
    val deltaY = tip.y - from.y
    if (deltaX == 0f && deltaY == 0f) return // Avoid division by zero if points coincide

    val angleRad = atan2(deltaY, deltaX) // Angle of the final segment

    // Arrowhead lines relative angle (e.g., +/- 30 degrees)
    val arrowAngle = (PI / 6).toFloat() // 30 degrees

    // Calculate points for the two lines of the arrowhead
    val point1X = tip.x - arrowHeadSize * cos(angleRad + arrowAngle)
    val point1Y = tip.y - arrowHeadSize * sin(angleRad + arrowAngle)

    val point2X = tip.x - arrowHeadSize * cos(angleRad - arrowAngle)
    val point2Y = tip.y - arrowHeadSize * sin(angleRad - arrowAngle)

    // Draw the arrowhead lines
    drawLine(color = color, start = Offset(point1X, point1Y), end = tip, strokeWidth = strokeWidth)
    drawLine(color = color, start = Offset(point2X, point2Y), end = tip, strokeWidth = strokeWidth)
}
