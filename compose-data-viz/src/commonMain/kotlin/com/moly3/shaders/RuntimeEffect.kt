package com.moly3.shaders

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap

interface RuntimeEffect {
    /** Indicates if the current platform is supported*/
    val supported: Boolean

    /** Defines if the effect is ready to be displayed */
    val ready: Boolean

    /** Sets a float array uniform for this shader */
    fun setFloatUniform(
        name: String,
        value1: Float,
    ) {}

    /** Sets a float array uniform for this shader */
    fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
    ) {}

    /** Sets a float array uniform for this shader */
    fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
    ) {}

    fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
        value4: Float,
    ) {}

    /** Sets a float array uniform for this shader */
    fun setFloatUniform(
        name: String,
        values: FloatArray,
    ) {}

    /** Updates the uniforms for the shader, on changes of the size or time.*/
    fun update(
        shader: Shader,
        time: Float,
        width: Float,
        height: Float,
    ) {}

    fun setImageUniform(
        name: String,
        image: ImageBitmap,
    ) {}

    /** Builds an updates ShaderBrush*/
    fun build(): Brush

    fun buildShader(): androidx.compose.ui.graphics.Shader
}

internal expect fun buildEffect(shader: Shader): RuntimeEffect
