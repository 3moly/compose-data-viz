package com.moly3.dataviz.func

import androidx.compose.ui.geometry.Offset

private fun isPointNearCubicBezier(
    p: Offset,
    p0: Offset, p1: Offset, p2: Offset, p3: Offset,
    threshold: Float,
    segments: Int = 20
): Boolean {
    var minDistance = Float.MAX_VALUE
    for (i in 0..segments) {
        val t = i / segments.toFloat()
        val x = cubicBezier(t, p0.x, p1.x, p2.x, p3.x)
        val y = cubicBezier(t, p0.y, p1.y, p2.y, p3.y)
        val pointOnCurve = Offset(x, y)
        val dist = (p - pointOnCurve).getDistance()
        if (dist < minDistance) {
            minDistance = dist
            if (dist <= threshold) return true
        }
    }
    return false
}

private fun cubicBezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
    val u = 1 - t
    return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
}

private fun isPointNearLineSegment(p: Offset, a: Offset, b: Offset, threshold: Float): Boolean {
    val ab = b - a
    val ap = p - a
    val abLengthSquared = ab.getDistanceSquared()
    val t = if (abLengthSquared != 0f) {
        ((ap.x * ab.x + ap.y * ab.y) / abLengthSquared).coerceIn(0f, 1f)
    } else 0f
    val projection = Offset(a.x + ab.x * t, a.y + ab.y * t)
    return (p - projection).getDistance() <= threshold
}

//fun isCursorNearArrow(
//    cursor: Offset,
//    startPoint: Offset,
//    endPoint: Offset,
//    fromSide: BoxSide,
//    toSide: BoxSide,
//    config: ConnectionConfig,
//    zoom: Float,
//    density: Density,
//): Boolean {
//    val stubLength = config.stubLength
//    val maxArcHeight = config.maxArcHeight
//    val hitThreshold = config.hitThreshold
//    val controlPointFactor = config.controlPointFactor
//
//    val hitThresholdInPx = density.run { hitThreshold.toPx() }
//    val scaledStubLength = stubLength * zoom
//    val scaledMaxArcHeight = maxArcHeight * zoom
//    val scaledThreshold = hitThresholdInPx * zoom
//
//    val (stubStartPoint, curveStartPoint) = calculateStubAndCurvePoints(
//        startPoint,
//        fromSide,
//        scaledStubLength
//    )
//    val (stubEndPoint, curveEndPoint) = calculateStubAndCurvePoints(
//        endPoint,
//        toSide,
//        scaledStubLength
//    )
//
//    val controlPoints = calculateControlPoints(
//        curveStartPoint,
//        curveEndPoint,
//        fromSide,
//        toSide,
//        controlPointFactor,
//        startPoint,
//        endPoint,
//        scaledMaxArcHeight
//    )
//
//    return isPointNearLineSegment(cursor, stubStartPoint, curveStartPoint, scaledThreshold) ||
//            isPointNearCubicBezier(
//                cursor,
//                curveStartPoint,
//                controlPoints.first,
//                controlPoints.second,
//                curveEndPoint,
//                scaledThreshold
//            ) ||
//            isPointNearLineSegment(cursor, curveEndPoint, stubEndPoint, scaledThreshold)
//}