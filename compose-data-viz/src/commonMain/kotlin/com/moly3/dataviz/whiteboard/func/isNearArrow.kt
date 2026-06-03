package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.whiteboard.model.BoxSide
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.func.calculateControlPoints
import com.moly3.dataviz.func.calculateStubAndCurvePoints
import kotlin.math.sqrt

private const val LINE_LENGTH_EPSILON = 0.0001f
private const val BEZIER_DEFAULT_SAMPLES = 30
private const val BEZIER_COEFFICIENT = 3f

/**
 * Determines if a cursor position is near an arrow.
 */
fun isNearArrow(
    cursor: Offset,
    startPoint: Offset,
    endPoint: Offset,
    fromSide: BoxSide,
    toSide: BoxSide,
    density: Density,
    config: ConnectionConfig,
    zoom: Float,
): Boolean {
    val stubLengthPx = density.run { config.stubLength.dp.toPx() } * zoom
    val hitThresholdPx = density.run { config.hitThreshold.toPx() } * zoom
    val maxArcHeightPx = density.run { config.maxArcHeight.dp.toPx() } * zoom

    val (stubStartPoint, curveStartPoint) =
        calculateStubAndCurvePoints(startPoint, fromSide, stubLengthPx)
    val (stubEndPoint, curveEndPoint) =
        calculateStubAndCurvePoints(endPoint, toSide, stubLengthPx)

    if (isPointNearLineSegment(cursor, stubStartPoint, curveStartPoint, hitThresholdPx)) return true
    if (isPointNearLineSegment(cursor, curveEndPoint, stubEndPoint, hitThresholdPx)) return true

    val controlPoints =
        calculateControlPoints(
            curveStartPoint,
            curveEndPoint,
            fromSide,
            toSide,
            config.controlPointFactor,
            startPoint,
            endPoint,
            maxArcHeightPx,
        )

    return isPointNearCubicBezier(
        cursor,
        curveStartPoint,
        controlPoints.first,
        controlPoints.second,
        curveEndPoint,
        hitThresholdPx,
    )
}

private fun isPointNearLineSegment(
    point: Offset,
    lineStart: Offset,
    lineEnd: Offset,
    threshold: Float,
): Boolean {
    val lineVec = Offset(lineEnd.x - lineStart.x, lineEnd.y - lineStart.y)
    val pointVec = Offset(point.x - lineStart.x, point.y - lineStart.y)

    val lineLength = sqrt(lineVec.x * lineVec.x + lineVec.y * lineVec.y)
    if (lineLength < LINE_LENGTH_EPSILON) {
        return distanceBetween(point, lineStart) <= threshold
    }

    val lineUnitVec = Offset(lineVec.x / lineLength, lineVec.y / lineLength)
    val projection = pointVec.x * lineUnitVec.x + pointVec.y * lineUnitVec.y

    val closestPoint: Offset =
        when {
            projection < 0 -> {
                lineStart
            }

            projection > lineLength -> {
                lineEnd
            }

            else -> {
                Offset(
                    lineStart.x + projection * lineUnitVec.x,
                    lineStart.y + projection * lineUnitVec.y,
                )
            }
        }

    return distanceBetween(point, closestPoint) <= threshold
}

private fun distanceBetween(
    p1: Offset,
    p2: Offset,
): Float {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}

private fun isPointNearCubicBezier(
    point: Offset,
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    threshold: Float,
    numSamples: Int = BEZIER_DEFAULT_SAMPLES,
): Boolean {
    var prevPoint = p0
    for (i in 1..numSamples) {
        val t = i.toFloat() / numSamples
        val mt = 1 - t
        val mt2 = mt * mt
        val mt3 = mt2 * mt
        val t2 = t * t
        val t3 = t2 * t

        val x = mt3 * p0.x + BEZIER_COEFFICIENT * mt2 * t * p1.x + BEZIER_COEFFICIENT * mt * t2 * p2.x + t3 * p3.x
        val y = mt3 * p0.y + BEZIER_COEFFICIENT * mt2 * t * p1.y + BEZIER_COEFFICIENT * mt * t2 * p2.y + t3 * p3.y

        val currPoint = Offset(x, y)

        if (isPointNearLineSegment(point, prevPoint, currPoint, threshold)) {
            return true
        }
        prevPoint = currPoint
    }
    return false
}
