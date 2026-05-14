package com.moly3.dataviz.func

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

@Composable
fun <T> rememberPainterFromComposable(
    modifier: Modifier = Modifier,
    captureKey: T? = null,
    content: @Composable () -> Unit
): Painter? {
    val graphicsLayer = rememberGraphicsLayer()
    var capturedPainter by remember { mutableStateOf<Painter?>(null) }
    var layerHasContent by remember { mutableStateOf(false) }
    var captureTick by remember { mutableStateOf(0) }

    // Self-mounting: SubcomposeLayout gives us a real measure/layout/draw pass
    // for `content`, but we place it at IntOffset(-1_000_000, -1_000_000) so
    // it's effectively off-screen and never visible to the user.
    SubcomposeLayout(modifier = Modifier) { constraints ->
        // Let content size itself freely up to a sane max, instead of being
        // clamped by a tiny parent.
        val loose = androidx.compose.ui.unit.Constraints()  // fully unbounded
        val measurables = subcompose("captureSlot") {
            androidx.compose.foundation.layout.Box(
                modifier = modifier.drawWithContent {
                    if (size.width > 0f && size.height > 0f) {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        if (!layerHasContent) layerHasContent = true
                        captureTick++
                    }
                }
            ) { content() }
        }
        val placeables = measurables.map { it.measure(loose) }
        layout(0, 0) {
            placeables.forEach { it.place(IntOffset(-1_000_000, -1_000_000)) }
        }
    }

    // Recapture whenever layer content changes (captureKey or a fresh draw).
    LaunchedEffect(graphicsLayer, captureKey, layerHasContent) {
        if (!layerHasContent) return@LaunchedEffect
        withFrameNanos { }  // wait one frame so the record() has run
        runCatching {
            capturedPainter = BitmapPainter(graphicsLayer.toImageBitmap())
        }
    }

    return capturedPainter
}