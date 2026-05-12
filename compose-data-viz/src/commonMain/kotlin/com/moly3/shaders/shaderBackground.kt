package com.moly3.shaders

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.round

/**
 * Draw's the shader as background via the [drawBehind] modifier.
 *
 * When running on Android 13 or newer (Tiramisu), usage of this API renders the shader.
 * On older Android devices, the provided [fallback] Brush is used instead.
 *
 * @param shader Shader to use to draw. [Shader] class. Example [com.mikepenz.hypnoticcanvas.shaders.GlossyGradients].
 * @param speed Adjusts how fast the shader is animated
 * @param fallback The fallback brush to draw on unsupported devices
 */
@Composable
fun Modifier.shaderBackground(
    shader: Shader,
    speed: Float = 1f,
    fallback: () -> Brush = {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    },
): Modifier {
    val runtimeEffect = remember(shader) { buildEffect(shader) }
    var size: Size by remember { mutableStateOf(Size(-1f, -1f)) }
    val speedModifier = shader.speedModifier

    val time by if (runtimeEffect.supported) {
        var startMillis = remember(shader) { -1L }
        produceState(0f, speedModifier) {
            while (true) {
                withInfiniteAnimationFrameMillis {
                    if (startMillis < 0) startMillis = it
                    value = ((it - startMillis) / 16.6f) / 10f
                }
            }
        }
    } else {
        mutableStateOf(-1f)
    }

    return this then Modifier.onGloballyPositioned {
        size = Size(it.size.width.toFloat(), it.size.height.toFloat())
    }.drawBehind {
        runtimeEffect.update(shader, (time * speed * speedModifier).round(3), size.width, size.height) // set uniforms for the shaders
        if (runtimeEffect.ready) {

            drawRect(brush = runtimeEffect.build())
        } else {
            drawRect(brush = fallback())
        }
    }
}

fun Float.round(decimals: Int): Float {
    var multiplier = 1.0f
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}