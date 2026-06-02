package com.moly3.dataviz.graph.ui

import androidx.compose.ui.graphics.Path

class GraphBuffers {
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
     * on every use in drawArrowHead so per-edge Path() allocation —
     * previously a real GC pressure point on large graphs — goes away.
     */
    val arrowPath: Path = Path()

    fun ensureCapacity(nodeCount: Int) {
        val reqVerts = nodeCount * 4
        val reqIndices = nodeCount * 6
        if (positions.size < reqVerts * 2) positions = FloatArray(reqVerts * 4)
        if (texCoords.size < reqVerts * 2) texCoords = FloatArray(reqVerts * 4)
        if (colors.size < reqVerts) colors = IntArray(reqVerts * 2)
        if (indices.size < reqIndices) indices = ShortArray(reqIndices * 2)
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
