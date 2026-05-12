package com.moly3.shaders

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.nativePaint

actual fun Canvas.drawVertices2(
    positions: FloatArray,
    colors: IntArray?,
    texCoords: FloatArray?,
    indices: ShortArray?,
    shader: androidx.compose.ui.graphics.Shader
) {
    val frameworkPaint = Paint().apply {
        this.shader = shader
    }.nativePaint

    nativeCanvas.drawVertices(
        android.graphics.Canvas.VertexMode.TRIANGLES, // Use Android's native VertexMode
        positions.size,
        positions,
        0,                 // vertOffset
        texCoords,
        0,                 // texOffset
        colors,
        0,                 // colorOffset
        indices,
        0,                 // indexOffset
        indices?.size ?: 0, // indexCount
        frameworkPaint
    )
}