package com.moly3.dataviz.core.graph.engine.impl.ultra

import kotlin.math.max
import kotlin.math.sqrt

class UltraFastQuadTree(initialCapacity: Int = 1024) {
    var nodeX = FloatArray(initialCapacity)
    var nodeY = FloatArray(initialCapacity)
    var nodeHalf = FloatArray(initialCapacity)
    var nodeMass = FloatArray(initialCapacity)
    var nodeComX = FloatArray(initialCapacity)
    var nodeComY = FloatArray(initialCapacity)

    var firstChild = IntArray(initialCapacity) { -1 }
    var nodeBody = IntArray(initialCapacity) { -1 }

    var count = 0
    var rootIndex = 0

    private var posXRef: FloatArray? = null
    private var posYRef: FloatArray? = null

    private fun ensureCapacity(needed: Int) {
        if (needed <= nodeX.size) return
        var newSize = nodeX.size
        while (newSize < needed) newSize *= 2

        nodeX = nodeX.copyOf(newSize)
        nodeY = nodeY.copyOf(newSize)
        nodeHalf = nodeHalf.copyOf(newSize)
        nodeMass = nodeMass.copyOf(newSize)
        nodeComX = nodeComX.copyOf(newSize)
        nodeComY = nodeComY.copyOf(newSize)

        val oldFC = firstChild
        firstChild = IntArray(newSize) { -1 }
        oldFC.copyInto(firstChild)

        val oldBody = nodeBody
        nodeBody = IntArray(newSize) { -1 }
        oldBody.copyInto(nodeBody)
    }

    fun build(positionsX: FloatArray, positionsY: FloatArray, n: Int) {
        count = 0
        if (n == 0) return

        posXRef = positionsX
        posYRef = positionsY

        // Pre-size capacity: worst case ~4n nodes for full tree
        ensureCapacity(n * 4 + 16)

        var minX = positionsX[0]; var maxX = positionsX[0]
        var minY = positionsY[0]; var maxY = positionsY[0]
        for (i in 1 until n) {
            val x = positionsX[i]; val y = positionsY[i]
            if (x < minX) minX = x else if (x > maxX) maxX = x
            if (y < minY) minY = y else if (y > maxY) maxY = y
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val half = max(maxX - minX, maxY - minY) * 0.5f + 1f

        rootIndex = newNode(cx, cy, half)
        for (i in 0 until n) {
            insert(rootIndex, i, positionsX[i], positionsY[i], 0)
        }
    }

    private fun newNode(cx: Float, cy: Float, half: Float): Int {
        ensureCapacity(count + 1)
        val i = count++
        nodeX[i] = cx; nodeY[i] = cy; nodeHalf[i] = half
        nodeMass[i] = 0f; nodeComX[i] = 0f; nodeComY[i] = 0f
        firstChild[i] = -1; nodeBody[i] = -1
        return i
    }

    private fun insert(nodeIdx: Int, bodyIdx: Int, x: Float, y: Float, depth: Int, updateMass: Boolean = true) {
        if (updateMass) {
            val mass = nodeMass[nodeIdx]
            val newMass = mass + 1f
            nodeComX[nodeIdx] = (nodeComX[nodeIdx] * mass + x) / newMass
            nodeComY[nodeIdx] = (nodeComY[nodeIdx] * mass + y) / newMass
            nodeMass[nodeIdx] = newMass
        }

        val existingBody = nodeBody[nodeIdx]
        val fc = firstChild[nodeIdx]

        if (fc == -1 && existingBody == -1) {
            nodeBody[nodeIdx] = bodyIdx
            return
        }

        if (fc == -1 && existingBody != -1) {
            if (depth >= 16) return

            val newHalf = nodeHalf[nodeIdx] * 0.5f
            val cx = nodeX[nodeIdx]; val cy = nodeY[nodeIdx]
            ensureCapacity(count + 4)
            val first = count
            newNode(cx - newHalf, cy - newHalf, newHalf)
            newNode(cx + newHalf, cy - newHalf, newHalf)
            newNode(cx - newHalf, cy + newHalf, newHalf)
            newNode(cx + newHalf, cy + newHalf, newHalf)
            firstChild[nodeIdx] = first

            val exX = posXRef!![existingBody]; val exY = posYRef!![existingBody]
            val exChild = first + (if (exX >= cx) 1 else 0) + (if (exY >= cy) 2 else 0)
            insert(exChild, existingBody, exX, exY, depth + 1, updateMass = false)
            nodeBody[nodeIdx] = -1
        }

        val fcUpdated = firstChild[nodeIdx]
        val targetChild = fcUpdated + (if (x >= nodeX[nodeIdx]) 1 else 0) + (if (y >= nodeY[nodeIdx]) 2 else 0)
        insert(targetChild, bodyIdx, x, y, depth + 1, updateMass = true)
    }

    /**
     * Iterative repulsion traversal. The stack needs to be sized for worst-case
     * depth; 256 was risky for very dense graphs. Caller should pass a stack
     * sized at least 4 * tree depth. log4(5000) ~ 6, but skewed distributions
     * push deeper. 512 is safer.
     */
    fun computeRepulsionIterative(
        x: Float, y: Float, bodyIdx: Int,
        repelStrength: Float, softening: Float,
        thetaSq: Float, stack: IntArray, outForce: FloatArray
    ) {
        outForce[0] = 0f; outForce[1] = 0f
        if (count == 0) return

        var stackSize = 0
        stack[stackSize++] = rootIndex

        var fx = 0f; var fy = 0f
        val stackCap = stack.size

        while (stackSize > 0) {
            val nodeIdx = stack[--stackSize]
            val mass = nodeMass[nodeIdx]
            if (mass == 0f) continue

            val dx = nodeComX[nodeIdx] - x
            val dy = nodeComY[nodeIdx] - y
            val distSq = max(dx * dx + dy * dy, 0.01f)
            val size = nodeHalf[nodeIdx] * 2f

            val fc = firstChild[nodeIdx]
            val body = nodeBody[nodeIdx]

            if (fc == -1) {
                if (body != bodyIdx && body != -1) {
                    val dist = sqrt(distSq)
                    val ds = dist + softening
                    val mag = repelStrength * mass / (ds * ds)
                    val invDist = 1f / dist
                    fx -= dx * invDist * mag
                    fy -= dy * invDist * mag
                }
                continue
            }

            if (size * size < thetaSq * distSq) {
                val dist = sqrt(distSq)
                val ds = dist + softening
                val mag = repelStrength * mass / (ds * ds)
                val invDist = 1f / dist
                fx -= dx * invDist * mag
                fy -= dy * invDist * mag
            } else {
                // Bounds-check stack growth (silently skip rather than crash)
                if (stackSize + 4 <= stackCap) {
                    stack[stackSize++] = fc
                    stack[stackSize++] = fc + 1
                    stack[stackSize++] = fc + 2
                    stack[stackSize++] = fc + 3
                }
            }
        }
        outForce[0] = fx; outForce[1] = fy
    }
}