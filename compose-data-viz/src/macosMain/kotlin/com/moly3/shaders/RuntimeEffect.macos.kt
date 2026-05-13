package com.moly3.shaders

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode

class JvmRuntimeEffect(shader: Shader) : RuntimeEffect {
    private val compositeRuntimeEffect = org.jetbrains.skia.RuntimeEffect.makeForShader(shader.sksl)
    private val compositeShaderBuilder = RuntimeShaderBuilder(compositeRuntimeEffect)

    override val supported: Boolean = true
    override var ready: Boolean = false

    override fun setImageUniform(name: String, image: ImageBitmap) {
        // 1. Extract the native Skia Bitmap from Compose
        val skiaBitmap = image.asSkiaBitmap()

        // 2. Create a Skia Image
        val skiaImage = Image.makeFromBitmap(skiaBitmap)

        // 3. Create a Skia Shader from the image with Clamp behavior
        val skiaShader = skiaImage.makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.LINEAR
        )

        // 4. Bind it as a child shader in the SkSL builder
        compositeShaderBuilder.child(name, skiaShader)
    }


    override fun setFloatUniform(name: String, value1: Float) {
        compositeShaderBuilder.uniform(name, value1)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
        compositeShaderBuilder.uniform(name, value1, value2)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        compositeShaderBuilder.uniform(name, value1, value2, value3)
    }

    override fun setFloatUniform(
        name: String,
        value1: Float,
        value2: Float,
        value3: Float,
        value4: Float
    ) {
        compositeShaderBuilder.uniform(name, value1, value2, value3, value4)
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        compositeShaderBuilder.uniform(name, values)
    }

    override fun update(shader: Shader, time: Float, width: Float, height: Float) {
        shader.applyUniforms(this, time, width, height)
        ready = width > 0 && height > 0
    }

    override fun build(): Brush {
        return ShaderBrush(compositeShaderBuilder.makeShader().asComposeShader())
    }

    override fun buildShader(): androidx.compose.ui.graphics.Shader {
        return compositeShaderBuilder.makeShader().asComposeShader()
    }
}

internal actual fun buildEffect(shader: com.moly3.shaders.Shader): RuntimeEffect {
    return JvmRuntimeEffect(shader)
}