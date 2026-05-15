package com.moly3.dataviz.graph.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    var ppaineters = painters
    if (ppaineters.isEmpty()) {
        ppaineters = listOf(BitmapPainter(ImageBitmap(1, 1)))
    }
    val count = ppaineters.size
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

suspend fun createAtlasFromUrlsSuspend(
    urls: List<String>,
    density: Density,
    tileSizePx: Int = 128,
    concurrencyLimit: Int = 20, // Limits simultaneous image processing
    fallbackPainter: Painter = ColorPainter(Color.Transparent), // Keeps indices intact!
    imageLoader: suspend (url: String) -> Painter?
): TextureAtlas = coroutineScope {

    val semaphore = Semaphore(concurrencyLimit)

    // 1. Fire network requests with a concurrency limit
    val deferredPainters = urls.map { url ->
        async {
            semaphore.withPermit {
                try {
                    imageLoader(url) ?: fallbackPainter
                } catch (e: Exception) {
                    e.printStackTrace()
                    fallbackPainter // Return empty space instead of dropping
                }
            }
        }
    }

    // 2. Wait for all. NO filterNotNull() here!
    val painters = deferredPainters.awaitAll()

    // 3. Delegate to synchronous creation
    createSvgAtlas(
        painters = painters,
        density = density,
        tileSizePx = tileSizePx
    )
}