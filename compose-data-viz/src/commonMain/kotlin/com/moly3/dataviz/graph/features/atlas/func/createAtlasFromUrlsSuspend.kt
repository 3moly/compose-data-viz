package com.moly3.dataviz.graph.features.atlas.func

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import com.moly3.dataviz.graph.features.atlas.TextureAtlas
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

suspend fun createAtlasFromUrlsSuspend(
    urls: List<String>,
    density: Density,
    tileSizePx: Int = 128,
    concurrencyLimit: Int = 20,
    fallbackPainter: Painter = ColorPainter(Color.Transparent),
    imageLoader: suspend (url: String) -> Painter?,
): TextureAtlas =
    coroutineScope {
        val semaphore = Semaphore(concurrencyLimit)
        val deferredPainters =
            urls.map { url ->
                async {
                    semaphore.withPermit {
                        try {
                            imageLoader(url) ?: fallbackPainter
                        } catch (e: Exception) {
                            e.printStackTrace()
                            fallbackPainter
                        }
                    }
                }
            }
        val painters = deferredPainters.awaitAll()
        createSvgAtlas(
            painters = painters,
            density = density,
            tileSizePx = tileSizePx,
        )
    }
