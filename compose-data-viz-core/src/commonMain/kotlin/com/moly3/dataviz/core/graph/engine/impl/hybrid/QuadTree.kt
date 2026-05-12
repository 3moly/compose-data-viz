package com.moly3.dataviz.core.graph.engine.impl.hybrid;

import kotlin.math.max
import kotlin.math.sqrt

private const val THETA = 1.2f   // Higher = more approximation = faster (Obsidian uses ~1.0-1.5)
private const val THETA_SQ = THETA * THETA
private const val MAX_DEPTH = 16
private const val LEAF_CAPACITY = 1

internal class QuadTree {
    // Flat arrays instead of object tree - massively reduces allocation/GC pressure
    // Each "node" of the tree uses 8 floats: cx, cy, halfSize, mass, comX, comY, firstChild, bodyIndex
    private var nodeX = FloatArray(1024)
    private var nodeY = FloatArray(1024)
    private var nodeHalf = FloatArray(1024)
    private var nodeMass = FloatArray(1024)
    private var nodeComX = FloatArray(1024)
    private var nodeComY = FloatArray(1024)

    // firstChild: index of first of 4 children (or -1 if leaf)
    private var nodeFirstChild = IntArray(1024) { -1 }

    // bodyIndex: index into bodies if leaf with one body, else -1
    private var nodeBody = IntArray(1024) { -1 }

    private var count = 0
    private var rootIndex = 0

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
        val oldFC = nodeFirstChild
        nodeFirstChild = IntArray(newSize) { -1 }
        oldFC.copyInto(nodeFirstChild)
        val oldBody = nodeBody
        nodeBody = IntArray(newSize) { -1 }
        oldBody.copyInto(nodeBody)
    }

    private fun newNode(cx: Float, cy: Float, half: Float): Int {
        ensureCapacity(count + 1)
        val i = count++
        nodeX[i] = cx
        nodeY[i] = cy
        nodeHalf[i] = half
        nodeMass[i] = 0f
        nodeComX[i] = 0f
        nodeComY[i] = 0f
        nodeFirstChild[i] = -1
        nodeBody[i] = -1
        return i
    }

    fun build(positionsX: FloatArray, positionsY: FloatArray, n: Int) {
        count = 0
        if (n == 0) return

        // Find bounds
        var minX = positionsX[0];
        var maxX = positionsX[0]
        var minY = positionsY[0];
        var maxY = positionsY[0]
        for (i in 1 until n) {
            val x = positionsX[i];
            val y = positionsY[i]
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

    private fun insert(nodeIdx: Int, bodyIdx: Int, x: Float, y: Float, depth: Int) {
        val existingBody = nodeBody[nodeIdx]
        val firstChild = nodeFirstChild[nodeIdx]

        // Update center of mass incrementally
        val mass = nodeMass[nodeIdx]
        val newMass = mass + 1f
        nodeComX[nodeIdx] = (nodeComX[nodeIdx] * mass + x) / newMass
        nodeComY[nodeIdx] = (nodeComY[nodeIdx] * mass + y) / newMass
        nodeMass[nodeIdx] = newMass

        if (firstChild == -1 && existingBody == -1) {
            // Empty leaf -> place body here
            nodeBody[nodeIdx] = bodyIdx
            return
        }

        if (firstChild == -1 && existingBody != -1) {
            if (depth >= MAX_DEPTH) {
                // Too deep - just accumulate mass (already done above), don't subdivide
                return
            }
            // Subdivide and re-insert existing body
            subdivide(nodeIdx)
            // We need original position of existing body to re-insert; caller provides that via stored arrays
            // We'll handle by calling reinsertBody
            // Mass was already counted for existing body during its first insert,
            // so we don't increment again - subdivide just routes it down.
            // But we DID increment mass for new body above, so we need to undo for existing
            // Actually: when we placed existingBody, we incremented mass to 1.
            // Now we incremented to 2 for new body. Both are correct totals.
            // We just need to route existing body to a child without touching mass again.
            routeToChild(
                nodeIdx,
                existingBody,
                posXRef!![existingBody],
                posYRef!![existingBody],
                depth,
                addMass = false
            )
            nodeBody[nodeIdx] = -1
        }

        // Now this node has children - route the new body
        routeToChild(nodeIdx, bodyIdx, x, y, depth, addMass = false)
    }

    // Reference to position arrays during build (set in build() if needed)
    private var posXRef: FloatArray? = null
    private var posYRef: FloatArray? = null

    fun buildWithRefs(positionsX: FloatArray, positionsY: FloatArray, n: Int) {
        posXRef = positionsX
        posYRef = positionsY
        build(positionsX, positionsY, n)
    }

    private fun subdivide(nodeIdx: Int) {
        val cx = nodeX[nodeIdx]
        val cy = nodeY[nodeIdx]
        val newHalf = nodeHalf[nodeIdx] * 0.5f
        ensureCapacity(count + 4)
        val first = count
        // Children order: NW, NE, SW, SE
        newNode(cx - newHalf, cy - newHalf, newHalf)
        newNode(cx + newHalf, cy - newHalf, newHalf)
        newNode(cx - newHalf, cy + newHalf, newHalf)
        newNode(cx + newHalf, cy + newHalf, newHalf)
        nodeFirstChild[nodeIdx] = first
    }

    private fun routeToChild(
        nodeIdx: Int,
        bodyIdx: Int,
        x: Float,
        y: Float,
        depth: Int,
        addMass: Boolean
    ) {
        val cx = nodeX[nodeIdx]
        val cy = nodeY[nodeIdx]
        val firstChild = nodeFirstChild[nodeIdx]
        val childIdx = firstChild + (if (x >= cx) 1 else 0) + (if (y >= cy) 2 else 0)

        if (addMass) {
            val m = nodeMass[childIdx]
            val nm = m + 1f
            nodeComX[childIdx] = (nodeComX[childIdx] * m + x) / nm
            nodeComY[childIdx] = (nodeComY[childIdx] * m + y) / nm
            nodeMass[childIdx] = nm
        }

        insertWithoutMassUpdate(childIdx, bodyIdx, x, y, depth + 1)
    }

    private fun insertWithoutMassUpdate(
        nodeIdx: Int,
        bodyIdx: Int,
        x: Float,
        y: Float,
        depth: Int
    ) {
        // Same as insert but skips its own mass update (caller handled it for routing case)
        val existingBody = nodeBody[nodeIdx]
        val firstChild = nodeFirstChild[nodeIdx]

        // Always update mass when entering a node during traversal
        val mass = nodeMass[nodeIdx]
        val newMass = mass + 1f
        nodeComX[nodeIdx] = (nodeComX[nodeIdx] * mass + x) / newMass
        nodeComY[nodeIdx] = (nodeComY[nodeIdx] * mass + y) / newMass
        nodeMass[nodeIdx] = newMass

        if (firstChild == -1 && existingBody == -1) {
            nodeBody[nodeIdx] = bodyIdx
            return
        }

        if (firstChild == -1 && existingBody != -1) {
            if (depth >= MAX_DEPTH) return
            subdivide(nodeIdx)
            routeToChild(
                nodeIdx,
                existingBody,
                posXRef!![existingBody],
                posYRef!![existingBody],
                depth,
                addMass = false
            )
            nodeBody[nodeIdx] = -1
        }

        routeToChild(nodeIdx, bodyIdx, x, y, depth, addMass = false)
    }

    /**
     * Compute repulsive force on body at (x, y) from all other bodies in tree.
     * Uses Barnes-Hut approximation: distant cells treated as single point at center of mass.
     * Returns force as (fx, fy) packed in a long-lived FloatArray of size 2 (caller-allocated to avoid GC).
     */
    fun computeForce(
        x: Float, y: Float, bodyIdx: Int,
        repelStrength: Float, softening: Float,
        out: FloatArray
    ) {
        out[0] = 0f
        out[1] = 0f
        if (count == 0) return
        computeForceRecursive(rootIndex, x, y, bodyIdx, repelStrength, softening, out)
    }

    private fun computeForceRecursive(
        nodeIdx: Int, x: Float, y: Float, bodyIdx: Int,
        repelStrength: Float, softening: Float,
        out: FloatArray
    ) {
        val mass = nodeMass[nodeIdx]
        if (mass == 0f) return

        val dx = nodeComX[nodeIdx] - x
        val dy = nodeComY[nodeIdx] - y
        val distSq = dx * dx + dy * dy
        val size = nodeHalf[nodeIdx] * 2f

        val firstChild = nodeFirstChild[nodeIdx]
        val body = nodeBody[nodeIdx]

        // Leaf with single body
        if (firstChild == -1) {
            if (body == bodyIdx || body == -1) return
            // Direct calculation
            applyRepulsion(dx, dy, distSq, mass, repelStrength, softening, out)
            return
        }

        // Internal node: use approximation if cell is "far enough"
        // size/dist < theta  <=>  size² < theta² * distSq
        if (size * size < THETA_SQ * distSq) {
            applyRepulsion(dx, dy, distSq, mass, repelStrength, softening, out)
            return
        }

        // Otherwise recurse into children
        for (i in 0 until 4) {
            computeForceRecursive(firstChild + i, x, y, bodyIdx, repelStrength, softening, out)
        }
    }

    private inline fun applyRepulsion(
        dx: Float, dy: Float, distSq: Float, mass: Float,
        repelStrength: Float, softening: Float,
        out: FloatArray
    ) {
        val safeDistSq = max(distSq, 0.01f)
        val dist = sqrt(safeDistSq)
        val softened = (dist + softening)
        val invDist = 1f / dist
        // Force magnitude: repel * mass / (dist + softening)²
        val mag = repelStrength * mass / (softened * softened)
        // Direction: AWAY from other body (so subtract from out, since dx/dy point TOWARD other)
        out[0] -= dx * invDist * mag
        out[1] -= dy * invDist * mag
    }
}