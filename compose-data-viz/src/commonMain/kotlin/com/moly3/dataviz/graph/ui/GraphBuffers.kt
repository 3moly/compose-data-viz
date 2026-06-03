package com.moly3.dataviz.graph.ui

import androidx.compose.ui.graphics.Path

class GraphBuffers {
    companion object {
        private const val VERTICES_PER_QUAD = 4
        private const val INDICES_PER_QUAD = 6
        private const val BUFFER_GROWTH_FACTOR = 2
        private const val COORDINATE_COMPONENTS = 2
    }

    var positions = FloatArray(0)
    var texCoords = FloatArray(0)
    var colors = IntArray(0)
    var indices = ShortArray(0)

    private var exactPositions = FloatArray(0)
    private var exactTexCoords = FloatArray(0)
    private var exactColors = IntArray(0)
    private var exactIndices = ShortArray(0)

    /**
     * Reusable scratch path for arrow heads. Rewound (not re-allocated)
     * on every use in drawArrowHead.
     */
    val arrowPath: Path = Path()

    fun ensureCapacity(nodeCount: Int) {
        val reqVerts = nodeCount * VERTICES_PER_QUAD
        val reqIndices = nodeCount * INDICES_PER_QUAD

        if (positions.size < reqVerts * COORDINATE_COMPONENTS) {
            positions = FloatArray(reqVerts * VERTICES_PER_QUAD)
        }
        if (texCoords.size < reqVerts * COORDINATE_COMPONENTS) {
            texCoords = FloatArray(reqVerts * VERTICES_PER_QUAD)
        }
        if (colors.size < reqVerts) {
            colors = IntArray(reqVerts * BUFFER_GROWTH_FACTOR)
        }
        if (indices.size < reqIndices) {
            indices = ShortArray(reqIndices * BUFFER_GROWTH_FACTOR)
        }
    }

    fun getExactPositions(size: Int): FloatArray {
        if (exactPositions.size != size) exactPositions = FloatArray(size)
        return exactPositions
    }

    fun getExactTexCoords(size: Int): FloatArray {
        if (exactTexCoords.size != size) exactTexCoords = FloatArray(size)
        return exactTexCoords
    }

    fun getExactColors(size: Int): IntArray {
        if (exactColors.size != size) exactColors = IntArray(size)
        return exactColors
    }

    fun getExactIndices(size: Int): ShortArray {
        if (exactIndices.size != size) exactIndices = ShortArray(size)
        return exactIndices
    }
}
