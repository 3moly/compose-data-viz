package com.moly3.dataviz.core.graph.hull

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Fast angular-sweep hull. Produces a star-shaped polygon around the group
 * centroid by bucketing points by angle and keeping the farthest point per
 * bucket, then connecting them in angular order.
 */
internal object AngularHullBuilder {
    private const val MIN_POINTS_FOR_HULL = 3
    private const val EPSILON = 1e-3f

    fun build(
        pointsXY: FloatArray,
        sectors: Int,
        padding: Float = 0f,
    ): HullResult? {
        val n = pointsXY.size / 2
        if (n < MIN_POINTS_FOR_HULL) return null

        // Centroid.
        var cx = 0f
        var cy = 0f
        for (i in 0 until n) {
            cx += pointsXY[i * 2]
            cy += pointsXY[i * 2 + 1]
        }
        cx /= n
        cy /= n

        // Per-sector farthest point.
        val farR2 = FloatArray(sectors)
        val farX = FloatArray(sectors)
        val farY = FloatArray(sectors)
        val twoPi = (2f * PI).toFloat()
        val invSector = sectors / twoPi

        for (i in 0 until n) {
            val x = pointsXY[i * 2]
            val y = pointsXY[i * 2 + 1]
            val dx = x - cx
            val dy = y - cy
            val r2 = dx * dx + dy * dy
            if (r2 == 0f) continue

            var ang = atan2(dy, dx)
            if (ang < 0f) ang += twoPi
            var s = (ang * invSector).toInt()
            if (s >= sectors) s = sectors - 1

            if (r2 > farR2[s]) {
                farR2[s] = r2
                farX[s] = x
                farY[s] = y
            }
        }

        val path = Path()
        var started = false
        var first = true
        var anchorX = 0f
        var anchorY = Float.POSITIVE_INFINITY

        for (s in 0 until sectors) {
            if (farR2[s] == 0f) continue
            var px = farX[s]
            var py = farY[s]
            if (padding != 0f) {
                val dx = px - cx
                val dy = py - cy
                val r = sqrt(dx * dx + dy * dy)
                if (r > EPSILON) {
                    val scale = (r + padding) / r
                    px = cx + dx * scale
                    py = cy + dy * scale
                }
            }
            if (py < anchorY) {
                anchorY = py
                anchorX = px
            }
            if (first) {
                path.moveTo(px, py)
                first = false
            } else {
                path.lineTo(px, py)
            }
            started = true
        }
        if (!started) return null
        path.close()

        return HullResult(
            path = path,
            labelAnchor = Offset(anchorX, anchorY),
        )
    }
}
