package com.moly3.shaders

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * No-op implementation of the Runtime effect for devices not supporting the [RuntimeShader].
 */
internal class FallbackAndroidRuntimeEffect : RuntimeEffect {
    override val supported: Boolean = false
    override var ready: Boolean = false

    override fun build(): Brush = Brush.horizontalGradient(listOf(Color.White, Color.White))

    override fun buildShader(): androidx.compose.ui.graphics.Shader = throw UnsupportedOperationException()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidRuntimeEffect(
    shader: Shader,
) : RuntimeEffect {
    private val compositeRuntimeEffect = RuntimeShader(shader.sksl)

    override val supported: Boolean = true
    override var ready: Boolean = false

    override fun setFloatUniform(
        name: String,
        value1: Float,
    ) {
        compositeRuntimeEffect.setFloatUniform(name, value1)
    }

    override fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
    ) {
        compositeRuntimeEffect.setFloatUniform(name, value1, value2)
    }

    override fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
    ) {
        compositeRuntimeEffect.setFloatUniform(name, value1, value2, value3)
    }

    override fun setImageUniform(
        name: String,
        image: ImageBitmap,
    ) {
        // 1. Extract native Android Bitmap
        val androidBitmap = image.asAndroidBitmap()

        // 2. Create native Android BitmapShader
        val bitmapShader = BitmapShader(androidBitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)

        // 3. Bind to AGSL
        compositeRuntimeEffect.setInputShader(name, bitmapShader)
    }

    override fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
        value4: Float,
    ) {
        compositeRuntimeEffect.setFloatUniform(name, value1, value2, value3, value4)
    }

    override fun setFloatUniform(
        name: String,
        values: FloatArray,
    ) {
        compositeRuntimeEffect.setFloatUniform(name, values)
    }

    override fun update(
        shader: Shader,
        time: Float,
        width: Float,
        height: Float,
    ) {
        shader.applyUniforms(this, time, width, height)
        ready = width > 0 && height > 0
    }

    override fun build(): Brush = ShaderBrush(compositeRuntimeEffect)

    override fun buildShader(): androidx.compose.ui.graphics.Shader = compositeRuntimeEffect
}

internal actual fun buildEffect(shader: Shader): RuntimeEffect =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AndroidRuntimeEffect(shader)
    } else {
        FallbackAndroidRuntimeEffect()
    }
