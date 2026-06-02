package com.moly3.shaders

import androidx.compose.ui.graphics.Canvas

expect fun Canvas.drawVertices2(
    positions: FloatArray,
    colors: IntArray?,
    texCoords: FloatArray?,
    indices: ShortArray?,
    shader: androidx.compose.ui.graphics.Shader,
)
