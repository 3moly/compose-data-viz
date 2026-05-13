package com.moly3.dataviz.graph.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.sqrt

class TextureAtlas(
    val imageBitmap: ImageBitmap,
    val tileSizePx: Int,
    val columns: Int
)

/**
 * Call this inside a @Composable to get access to LocalDensity, or pass Density directly.
 * Example: val painters = listOf(painterResource(Res.drawable.icon_1), ...)
 */
fun createSvgAtlas(
    painters: List<Painter>,
    density: Density,
    tileSizePx: Int = 128
): TextureAtlas {
    val count = painters.size
    val columns = ceil(sqrt(count.toDouble())).toInt()
    val rows = ceil(count.toDouble() / columns).toInt()

    val atlasWidth = columns * tileSizePx
    val atlasHeight = rows * tileSizePx

    // 1. Create a platform-agnostic ImageBitmap
    val imageBitmap = ImageBitmap(width = atlasWidth, height = atlasHeight)
    val canvas = Canvas(imageBitmap)
    val drawScope = CanvasDrawScope()

    // 2. Draw the Compose Painters (SVGs) onto the bitmap grid
    drawScope.draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(atlasWidth.toFloat(), atlasHeight.toFloat())
    ) {
        painters.forEachIndexed { index, painter ->
            val col = index % columns
            val row = index / columns

            translate(left = col * tileSizePx.toFloat(), top = row * tileSizePx.toFloat()) {
                with(painter) {
                    draw(size = Size(tileSizePx.toFloat(), tileSizePx.toFloat()))
                }
            }
        }
    }

    return TextureAtlas(imageBitmap, tileSizePx, columns)
}