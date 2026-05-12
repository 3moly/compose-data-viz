package com.moly3.shaders

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.skiaShader

actual fun Canvas.drawVertices2(
    positions: FloatArray,
    colors: IntArray?,
    texCoords: FloatArray?,
    indices: ShortArray?,
    shader:  androidx.compose.ui.graphics.Shader
) {
    skiaCanvas.drawVertices(
        org.jetbrains.skia.VertexMode.TRIANGLES,
        positions,
        colors,
        texCoords,
        indices,
        org.jetbrains.skia.BlendMode.MODULATE,
        org.jetbrains.skia.Paint().apply { this.shader = shader.skiaShader }
    )
}