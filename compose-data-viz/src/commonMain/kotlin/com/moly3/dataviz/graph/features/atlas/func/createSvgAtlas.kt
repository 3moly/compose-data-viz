package com.moly3.dataviz.graph.features.atlas.func

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.moly3.dataviz.graph.features.atlas.TextureAtlas
import kotlin.math.ceil
import kotlin.math.sqrt

fun createSvgAtlas(
    painters: List<Painter>,
    density: Density,
    tileSizePx: Int = 128
): TextureAtlas {
    var ppaineters = painters
    if (ppaineters.isEmpty()) {
        ppaineters = listOf(BitmapPainter(ImageBitmap(1, 1)))
    }
    val count = ppaineters.size
    val columns = ceil(sqrt(count.toDouble())).toInt()
    val rows = ceil(count.toDouble() / columns).toInt()

    val atlasWidth = columns * tileSizePx
    val atlasHeight = rows * tileSizePx
    val imageBitmap = ImageBitmap(width = atlasWidth, height = atlasHeight)
    val canvas = Canvas(imageBitmap)
    val drawScope = CanvasDrawScope()
    drawScope.draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(atlasWidth.toFloat(), atlasHeight.toFloat())
    ) {
        ppaineters.forEachIndexed { index, painter ->
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