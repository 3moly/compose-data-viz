package com.moly3.dataviz.core.graph.hull

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Build a smoothed, padded "island" path around a cloud of points.
 *
 * Pipeline:
 *   1. Special-case 1 / 2 / 3 points (bubble / capsule / triangle).
 *   2. Compute a concave hull via the k-nearest-neighbours method
 *      (Moreira & Santos, 2007 — robust and fast in practice).
 *   3. Inflate each hull vertex outward along its averaged-normal by `padding`.
 *   4. Smooth with closed Catmull-Rom → cubic Bezier conversion.
 *
 * All work is pure CPU, allocates only what the final Path needs (plus a few
 * small scratch arrays).  Safe to call from a background dispatcher.
 *
 * Input is a packed FloatArray [x0,y0, x1,y1, ...] for cache friendliness.
 */
internal object HullBuilder {
    /** Minimum number of vertices needed to form a polygon (concave or convex). */
    private const val MIN_HULL_POINTS = 3

    /** Smallest k for the k-NN concave-hull search. */
    private const val MIN_K = 3

    /** Largest k tried before giving up and falling back to a convex hull. */
    private const val MAX_K = 40

    /** Walk step at which the start vertex is freed so the hull can close on itself. */
    private const val CLOSE_ENABLE_STEP = 3

    /** Minimum radius, in px, for the single-point / two-point trivial shapes. */
    private const val MIN_RADIUS = 20f

    /** Lengths smaller than this are treated as zero to avoid divide-by-zero. */
    private const val MIN_LENGTH = 1e-3f

    /** Epsilon guarding the ray-cast denominator in [pointInPolygon]. */
    private const val RAY_CAST_EPSILON = 1e-6f

    /**
     * Catmull-Rom → cubic-Bezier tangent divisor; control points sit a third of
     * the way along the neighbouring chord.
     */
    private const val CATMULL_ROM_DIVISOR = 3f

    fun build(
        pointsXY: FloatArray,
        k: Int,
        padding: Float,
        smoothing: Float,
    ): HullResult? {
        val n = pointsXY.size / 2
        if (n == 0) return null

        return when (n) {
            1 -> {
                bubble(pointsXY[0], pointsXY[1], padding)
            }

            2 -> {
                // Packed layout [x0, y0, x1, y1]; point 1 sits at stride offsets.
                capsule(
                    pointsXY[0],
                    pointsXY[1],
                    pointsXY[1 * 2],
                    pointsXY[1 * 2 + 1],
                    padding,
                )
            }

            else -> {
                val hull =
                    concaveHull(pointsXY, k.coerceAtLeast(MIN_K))
                        ?: return convexFallback(pointsXY, padding, smoothing)
                val inflated = inflate(hull, padding)
                val path = smoothClosed(inflated, smoothing)
                val (anchorX, anchorY) = topMost(inflated)
                HullResult(path, Offset(anchorX, anchorY))
            }
        }
    }

    // ------------------------------------------------------------------------
    // Trivial cases
    // ------------------------------------------------------------------------

    private fun bubble(
        x: Float,
        y: Float,
        padding: Float,
    ): HullResult {
        val r = padding.coerceAtLeast(MIN_RADIUS)
        val p = Path().apply { addOval(Rect(x - r, y - r, x + r, y + r)) }
        return HullResult(p, Offset(x, y - r))
    }

    private fun capsule(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        padding: Float,
    ): HullResult {
        val r = padding.coerceAtLeast(MIN_RADIUS)
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(MIN_LENGTH)
        val nxv = -dy / len
        val nyv = dx / len
        // Build a stadium: arc-line-arc-line. Use Path arcs for simplicity.
        val p = Path()
        p.moveTo(x1 + nxv * r, y1 + nyv * r)
        p.lineTo(x2 + nxv * r, y2 + nyv * r)
        // Half-circle from (x2,y2)+n*r around (x2,y2) to (x2,y2)-n*r
        p.arcTo(
            Rect(x2 - r, y2 - r, x2 + r, y2 + r),
            startAngleDegrees = atan2(nyv, nxv) * 180f / PI.toFloat(),
            sweepAngleDegrees = -180f,
            forceMoveTo = false,
        )
        p.lineTo(x1 - nxv * r, y1 - nyv * r)
        p.arcTo(
            Rect(x1 - r, y1 - r, x1 + r, y1 + r),
            startAngleDegrees = atan2(-nyv, -nxv) * 180f / PI.toFloat(),
            sweepAngleDegrees = -180f,
            forceMoveTo = false,
        )
        p.close()
        val ax = (x1 + x2) * 0.5f
        val ay = min(y1, y2) - r
        return HullResult(p, Offset(ax, ay))
    }

    // ------------------------------------------------------------------------
    // Concave hull (k-NN / Moreira-Santos)
    // ------------------------------------------------------------------------

    private fun concaveHull(
        pointsXY: FloatArray,
        kStart: Int,
    ): FloatArray? {
        val n = pointsXY.size / 2
        if (n < MIN_HULL_POINTS) return null

        // Try increasing k until we get a valid simple polygon containing every point.
        var k = kStart.coerceAtMost(n - 1)
        val maxK = (n - 1).coerceAtMost(MAX_K)

        while (k <= maxK) {
            val hull = attemptHull(pointsXY, k)
            if (hull != null && allInside(pointsXY, hull)) return hull
            k++
        }
        return null
    }

    private fun attemptHull(
        pointsXY: FloatArray,
        k: Int,
    ): FloatArray? {
        val n = pointsXY.size / 2
        if (n < MIN_HULL_POINTS) return null

        // Find the lowest-Y starting point (max y in screen coords, but we don't
        // care about handedness here — just pick a guaranteed-hull-vertex).
        var startIdx = 0
        var minY = pointsXY[1]
        for (i in 1 until n) {
            val y = pointsXY[i * 2 + 1]
            if (y < minY) {
                minY = y
                startIdx = i
            }
        }

        val used = BooleanArray(n)
        val hull = ArrayList<Int>(n)
        hull.add(startIdx)
        used[startIdx] = true

        var current = startIdx
        var previousAngle = 0f // initial angle = 0 (pointing +x). After first step, becomes "back-direction" angle.
        var step = 1

        while (true) {
            if (step == CLOSE_ENABLE_STEP) used[startIdx] = false // allow closing the polygon

            val cx = pointsXY[current * 2]
            val cy = pointsXY[current * 2 + 1]

            // k nearest neighbours of `current` among unused points
            val knn = nearestK(pointsXY, current, used, k) ?: return null
            if (knn.isEmpty()) return null

            // Sort candidates by right-hand turn angle from previousAngle.
            // We want the candidate that makes the *largest right-hand turn*
            // (= smallest angular increment to the right).
            // Compute angle relative to previousAngle, then rotate so we pick min.
            data class Cand(
                val idx: Int,
                val turn: Float,
            )
            val cands = ArrayList<Cand>(knn.size)
            for (idx in knn) {
                val dx = pointsXY[idx * 2] - cx
                val dy = pointsXY[idx * 2 + 1] - cy
                val ang = atan2(dy, dx)
                // Right-hand turn relative to previousAngle:
                var turn = previousAngle - ang
                while (turn < 0f) turn += (2f * PI).toFloat()
                while (turn >= 2f * PI) turn -= (2f * PI).toFloat()
                cands.add(Cand(idx, turn))
            }
            cands.sortBy { it.turn }

            // Pick the first candidate that doesn't intersect any existing hull edge.
            var chosen = -1
            outer@ for (c in cands) {
                if (!segmentIntersectsHull(pointsXY, hull, current, c.idx)) {
                    chosen = c.idx
                    break@outer
                }
            }
            if (chosen == -1) return null // dead end → caller bumps k

            if (chosen == startIdx) {
                // Closed successfully
                break
            }

            hull.add(chosen)
            used[chosen] = true

            val dx = pointsXY[chosen * 2] - cx
            val dy = pointsXY[chosen * 2 + 1] - cy
            previousAngle = atan2(-dy, -dx) // back-direction angle
            current = chosen
            step++

            if (hull.size > n + 1) return null // safety
        }

        // Pack to FloatArray
        val out = FloatArray(hull.size * 2)
        for (i in hull.indices) {
            out[i * 2] = pointsXY[hull[i] * 2]
            out[i * 2 + 1] = pointsXY[hull[i] * 2 + 1]
        }
        return out
    }

    private fun nearestK(
        pointsXY: FloatArray,
        from: Int,
        used: BooleanArray,
        k: Int,
    ): IntArray? {
        val n = pointsXY.size / 2
        val fx = pointsXY[from * 2]
        val fy = pointsXY[from * 2 + 1]
        // Partial sort: collect (distSq, idx) for unused, then take k smallest.
        val items = ArrayList<LongArray>() // pack distBits + idx to avoid object churn? keep simple.
        val dists = ArrayList<Pair<Float, Int>>()
        for (i in 0 until n) {
            if (used[i] || i == from) continue
            val dx = pointsXY[i * 2] - fx
            val dy = pointsXY[i * 2 + 1] - fy
            dists.add((dx * dx + dy * dy) to i)
        }
        if (dists.isEmpty()) return null
        dists.sortBy { it.first }
        val kk = min(k, dists.size)
        val out = IntArray(kk)
        for (i in 0 until kk) out[i] = dists[i].second
        return out
    }

    private fun segmentIntersectsHull(
        pointsXY: FloatArray,
        hull: ArrayList<Int>,
        fromIdx: Int,
        toIdx: Int,
    ): Boolean {
        // Test segment (from→to) against every hull edge EXCEPT the last
        // edge that touches `from` (they share an endpoint).
        if (hull.size < 2) return false
        val ax = pointsXY[fromIdx * 2]
        val ay = pointsXY[fromIdx * 2 + 1]
        val bx = pointsXY[toIdx * 2]
        val by = pointsXY[toIdx * 2 + 1]
        // Iterate over hull edges (i, i+1)
        for (i in 0 until hull.size - 1) {
            val p = hull[i]
            val q = hull[i + 1]
            // Skip adjacent (sharing vertex with `from`)
            if (q == fromIdx) continue
            val px = pointsXY[p * 2]
            val py = pointsXY[p * 2 + 1]
            val qx = pointsXY[q * 2]
            val qy = pointsXY[q * 2 + 1]
            if (segmentsIntersect(ax, ay, bx, by, px, py, qx, qy)) return true
        }
        return false
    }

    private fun segmentsIntersect(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        dx: Float,
        dy: Float,
    ): Boolean {
        val d1 = cross(dx - cx, dy - cy, ax - cx, ay - cy)
        val d2 = cross(dx - cx, dy - cy, bx - cx, by - cy)
        val d3 = cross(bx - ax, by - ay, cx - ax, cy - ay)
        val d4 = cross(bx - ax, by - ay, dx - ax, dy - ay)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        ) {
            return true
        }
        return false
    }

    private fun cross(
        ux: Float,
        uy: Float,
        vx: Float,
        vy: Float,
    ): Float = ux * vy - uy * vx

    private fun allInside(
        pointsXY: FloatArray,
        hull: FloatArray,
    ): Boolean {
        val n = pointsXY.size / 2
        for (i in 0 until n) {
            if (!pointInPolygon(pointsXY[i * 2], pointsXY[i * 2 + 1], hull)) return false
        }
        return true
    }

    private fun pointInPolygon(
        px: Float,
        py: Float,
        hull: FloatArray,
    ): Boolean {
        // Standard ray cast. Points exactly on the boundary are considered inside.
        var inside = false
        val m = hull.size / 2
        var j = m - 1
        for (i in 0 until m) {
            val xi = hull[i * 2]
            val yi = hull[i * 2 + 1]
            val xj = hull[j * 2]
            val yj = hull[j * 2 + 1]
            if (((yi > py) != (yj > py)) &&
                (px < (xj - xi) * (py - yi) / ((yj - yi).takeIf { it != 0f } ?: RAY_CAST_EPSILON) + xi)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    // ------------------------------------------------------------------------
    // Fallback: convex hull (Andrew's monotone chain) — guaranteed to work.
    // ------------------------------------------------------------------------

    private fun convexFallback(
        pointsXY: FloatArray,
        padding: Float,
        smoothing: Float,
    ): HullResult? {
        val hull = convexHull(pointsXY) ?: return null
        val inflated = inflate(hull, padding)
        val path = smoothClosed(inflated, smoothing)
        val (ax, ay) = topMost(inflated)
        return HullResult(path, Offset(ax, ay))
    }

    private fun convexHull(pointsXY: FloatArray): FloatArray? {
        val n = pointsXY.size / 2
        if (n < MIN_HULL_POINTS) return null
        val idx = IntArray(n) { it }
        // Sort lexicographically by (x, y)
        val boxed = idx.toTypedArray()
        boxed.sortWith { a, b ->
            val ax = pointsXY[a * 2]
            val bx = pointsXY[b * 2]
            if (ax != bx) {
                ax.compareTo(bx)
            } else {
                pointsXY[a * 2 + 1].compareTo(pointsXY[b * 2 + 1])
            }
        }
        for (i in 0 until n) idx[i] = boxed[i]

        val h = IntArray(2 * n)
        var s = 0
        // Lower hull
        for (i in 0 until n) {
            val p = idx[i]
            while (s >= 2 && cross(
                    pointsXY[h[s - 1] * 2] - pointsXY[h[s - 2] * 2],
                    pointsXY[h[s - 1] * 2 + 1] - pointsXY[h[s - 2] * 2 + 1],
                    pointsXY[p * 2] - pointsXY[h[s - 2] * 2],
                    pointsXY[p * 2 + 1] - pointsXY[h[s - 2] * 2 + 1],
                ) <= 0
            ) {
                s--
            }
            h[s++] = p
        }
        val lower = s + 1
        // Upper hull
        for (i in n - 2 downTo 0) {
            val p = idx[i]
            while (s >= lower && cross(
                    pointsXY[h[s - 1] * 2] - pointsXY[h[s - 2] * 2],
                    pointsXY[h[s - 1] * 2 + 1] - pointsXY[h[s - 2] * 2 + 1],
                    pointsXY[p * 2] - pointsXY[h[s - 2] * 2],
                    pointsXY[p * 2 + 1] - pointsXY[h[s - 2] * 2 + 1],
                ) <= 0
            ) {
                s--
            }
            h[s++] = p
        }
        s-- // drop duplicate start
        if (s < MIN_HULL_POINTS) return null
        val out = FloatArray(s * 2)
        for (i in 0 until s) {
            out[i * 2] = pointsXY[h[i] * 2]
            out[i * 2 + 1] = pointsXY[h[i] * 2 + 1]
        }
        return out
    }

    // ------------------------------------------------------------------------
    // Inflate (offset outward along averaged normals)
    // ------------------------------------------------------------------------

    private fun inflate(
        hull: FloatArray,
        padding: Float,
    ): FloatArray {
        if (padding <= 0f) return hull
        val n = hull.size / 2

        // Determine winding (CCW vs CW). Compute signed area.
        var area2 = 0f
        var j = n - 1
        for (i in 0 until n) {
            area2 += (hull[j * 2] - hull[i * 2]) * (hull[j * 2 + 1] + hull[i * 2 + 1])
            j = i
        }
        // For CCW polygons in y-down screen coords, outward = (-edgeY, edgeX) rotated.
        // We don't need to know which — we always push *outward from the polygon centroid*.
        var ccx = 0f
        var ccy = 0f
        for (i in 0 until n) {
            ccx += hull[i * 2]
            ccy += hull[i * 2 + 1]
        }
        ccx /= n.toFloat()
        ccy /= n.toFloat()

        val out = FloatArray(n * 2)
        for (i in 0 until n) {
            val x = hull[i * 2]
            val y = hull[i * 2 + 1]
            val dx = x - ccx
            val dy = y - ccy
            val len = sqrt(dx * dx + dy * dy)
            if (len < MIN_LENGTH) {
                out[i * 2] = x
                out[i * 2 + 1] = y
                continue
            }
            val s = padding / len
            // Inflate from centroid: enough for visual padding without distorting too much.
            // Note: a true "Minkowski offset" would push along edge normals, but for
            // concave shapes that's prone to self-intersection.  Centroid push is robust.
            out[i * 2] = x + dx * s
            out[i * 2 + 1] = y + dy * s
        }
        return out
    }

    // ------------------------------------------------------------------------
    // Smoothing: closed Catmull-Rom → cubic Bezier
    // ------------------------------------------------------------------------

    private fun smoothClosed(
        hull: FloatArray,
        tension: Float,
    ): Path {
        val n = hull.size / 2
        val p = Path()
        if (n == 0) return p
        if (n == 1) {
            p.moveTo(hull[0], hull[1])
            return p
        }
        if (n == 2) {
            p.moveTo(hull[0], hull[1])
            // Packed layout [x0, y0, x1, y1]; point 1 sits at stride offsets.
            p.lineTo(hull[1 * 2], hull[1 * 2 + 1])
            p.close()
            return p
        }

        // Catmull-Rom with parameter alpha=0.5 (centripetal) → cubic Bezier control points.
        // For a closed curve, we wrap indices.
        val t = tension.coerceIn(0f, 1f)
        // Standard Catmull-Rom to Bezier: B1 = P1 + (P2-P0)*t/3 ; B2 = P2 - (P3-P1)*t/3
        // (uniform CR; centripetal would re-parameterize — visually equivalent for our use.)

        p.moveTo(hull[0], hull[1])
        for (i in 0 until n) {
            val i0 = (i - 1 + n) % n
            val i1 = i
            val i2 = (i + 1) % n
            val i3 = (i + 2) % n

            val p0x = hull[i0 * 2]
            val p0y = hull[i0 * 2 + 1]
            val p1x = hull[i1 * 2]
            val p1y = hull[i1 * 2 + 1]
            val p2x = hull[i2 * 2]
            val p2y = hull[i2 * 2 + 1]
            val p3x = hull[i3 * 2]
            val p3y = hull[i3 * 2 + 1]

            val c1x = p1x + (p2x - p0x) * (t / CATMULL_ROM_DIVISOR)
            val c1y = p1y + (p2y - p0y) * (t / CATMULL_ROM_DIVISOR)
            val c2x = p2x - (p3x - p1x) * (t / CATMULL_ROM_DIVISOR)
            val c2y = p2y - (p3y - p1y) * (t / CATMULL_ROM_DIVISOR)

            p.cubicTo(c1x, c1y, c2x, c2y, p2x, p2y)
        }
        p.close()
        return p
    }

    private fun topMost(hull: FloatArray): Pair<Float, Float> {
        var bestX = hull[0]
        var bestY = hull[1]
        val n = hull.size / 2
        for (i in 1 until n) {
            val y = hull[i * 2 + 1]
            if (y < bestY) {
                bestY = y
                bestX = hull[i * 2]
            }
        }
        return bestX to bestY
    }
}

internal data class HullResult(
    val path: Path,
    val labelAnchor: Offset,
)
