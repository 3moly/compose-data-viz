package com.moly3.shaders

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.round

private const val INITIAL_TIME_STAMP = -1L
private const val FRAME_DURATION_MS = 16.6f
private const val TIME_DIVISOR = 10f
private const val ROUNDING_DECIMALS = 3
private const val DECIMAL_MULTIPLIER_ITERATIONS = 10

/**
 * Draw's the shader as background via the [drawBehind] modifier.
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
        var startMillis = remember(shader) { INITIAL_TIME_STAMP }
        produceState(0f, speedModifier) {
            while (true) {
                withInfiniteAnimationFrameMillis {
                    if (startMillis < 0) startMillis = it
                    value = ((it - startMillis) / FRAME_DURATION_MS) / TIME_DIVISOR
                }
            }
        }
    } else {
        mutableStateOf(-1f)
    }

    return this then
        Modifier
            .onGloballyPositioned {
                size = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }.drawBehind {
                runtimeEffect.update(shader, (time * speed * speedModifier).round(ROUNDING_DECIMALS), size.width, size.height)
                if (runtimeEffect.ready) {
                    drawRect(brush = runtimeEffect.build())
                } else {
                    drawRect(brush = fallback())
                }
            }
}

private fun Float.round(decimals: Int): Float {
    var multiplier = 1f
    repeat(decimals) { multiplier *= DECIMAL_MULTIPLIER_ITERATIONS }
    return round(this * multiplier) / multiplier
}
