package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.block.model.BoxSide
import com.moly3.dataviz.core.block.model.ConnectionConfig
import com.moly3.dataviz.func.calculateControlPoints
import com.moly3.dataviz.func.calculateStubAndCurvePoints
import kotlin.math.sqrt

/**
 * Determines if a cursor position is near an arrow.
 *
 * @param cursor The position of the cursor
 * @param startPoint The start point of the arrow
 * @param endPoint The end point of the arrow
 * @param fromSide The side from which the arrow originates
 * @param toSide The side to which the arrow points
 * @param stubLength Length of the straight line segment extending from box edges
 * @param controlPointFactor Factor used for curve intensity (0-1)
 * @param hitThreshold Distance in pixels that determines if cursor is "near" the arrow
 * @param maxArcHeight Maximum allowed arc height
 * @return True if cursor is within hitThreshold distance of any part of the arrow path
 */
fun isNearArrow(
    cursor: Offset,
    startPoint: Offset,
    endPoint: Offset,
    fromSide: BoxSide,
    toSide: BoxSide,
    density: Density,
    config: ConnectionConfig,
    zoom: Float
): Boolean {
    // Convert Dp values to pixels with density scaling
//    val hitThresholdPx = density.run { config.hitThreshold.toPx() } * zoom
//    val stubLengthPx = config.stubLength * zoom
//    val maxArcHeightPx = config.maxArcHeight * zoom
    val stubLengthPx = density.run { config.stubLength.dp.toPx() } * zoom
    val hitThresholdPx = density.run { config.hitThreshold.toPx() } * zoom
    val maxArcHeightPx = density.run { config.maxArcHeight.dp.toPx() } * zoom

    // Calculate stub points
    val (stubStartPoint, curveStartPoint) = calculateStubAndCurvePoints(
        startPoint,
        fromSide,
        stubLengthPx
    )
    val (stubEndPoint, curveEndPoint) = calculateStubAndCurvePoints(
        endPoint,
        toSide,
        stubLengthPx
    )

    // Check if cursor is near line segments
    if (isPointNearLineSegment(cursor, stubStartPoint, curveStartPoint, hitThresholdPx)) {
        return true
    }
    if (isPointNearLineSegment(cursor, curveEndPoint, stubEndPoint, hitThresholdPx)) {
        return true
    }

    // Calculate control points for the Bezier curve
    val controlPoints = calculateControlPoints(
        curveStartPoint,
        curveEndPoint,
        fromSide,
        toSide,
        config.controlPointFactor,
        startPoint,
        endPoint,
        maxArcHeightPx
    )

    // Check if cursor is near the Bezier curve
    return isPointNearCubicBezier(
        cursor,
        curveStartPoint,
        controlPoints.first,
        controlPoints.second,
        curveEndPoint,
        hitThresholdPx
    )
}

/**
 * Checks if a point is near a line segment.
 */
private fun isPointNearLineSegment(
    point: Offset,
    lineStart: Offset,
    lineEnd: Offset,
    threshold: Float
): Boolean {
    // Vector from lineStart to lineEnd
    val lineVec = Offset(lineEnd.x - lineStart.x, lineEnd.y - lineStart.y)
    // Vector from lineStart to point
    val pointVec = Offset(point.x - lineStart.x, point.y - lineStart.y)

    val lineLength = sqrt(lineVec.x * lineVec.x + lineVec.y * lineVec.y)
    if (lineLength < 0.0001f) {
        // Line segment is too short, just check distance to start point
        return distanceBetween(point, lineStart) <= threshold
    }

    // Normalize the line vector
    val lineUnitVec = Offset(lineVec.x / lineLength, lineVec.y / lineLength)

    // Calculate projection of pointVec onto lineUnitVec
    val projection = pointVec.x * lineUnitVec.x + pointVec.y * lineUnitVec.y

    // Calculate closest point on line segment
    val closestPoint: Offset = when {
        projection < 0 -> lineStart // Before start of line segment
        projection > lineLength -> lineEnd // After end of line segment
        else -> Offset(
            lineStart.x + projection * lineUnitVec.x,
            lineStart.y + projection * lineUnitVec.y
        ) // On the line segment
    }

    // Calculate distance to closest point
    return distanceBetween(point, closestPoint) <= threshold
}

/**
 * Calculates the distance between two points.
 */
private fun distanceBetween(p1: Offset, p2: Offset): Float {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}

/**
 * Checks if a point is near a cubic Bezier curve.
 * Uses an approximation by sampling multiple points along the curve.
 */
private fun isPointNearCubicBezier(
    point: Offset,
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    threshold: Float,
    numSamples: Int = 30
): Boolean {
    // Sample points along the Bezier curve
    var prevPoint = p0
    for (i in 1..numSamples) {
        val t = i.toFloat() / numSamples
        // Cubic Bezier formula
        val mt = 1 - t
        val mt2 = mt * mt
        val mt3 = mt2 * mt
        val t2 = t * t
        val t3 = t2 * t

        val x = mt3 * p0.x + 3 * mt2 * t * p1.x + 3 * mt * t2 * p2.x + t3 * p3.x
        val y = mt3 * p0.y + 3 * mt2 * t * p1.y + 3 * mt * t2 * p2.y + t3 * p3.y

        val currPoint = Offset(x, y)

        // Check if point is near the line segment between consecutive sample points
        if (isPointNearLineSegment(point, prevPoint, currPoint, threshold)) {
            return true
        }

        prevPoint = currPoint
    }

    return false
}