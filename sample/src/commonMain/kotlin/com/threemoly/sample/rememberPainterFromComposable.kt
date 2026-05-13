package com.threemoly.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Renders any Composable into an ImageBitmap and returns it as a Painter.
 * This is fully KMP compatible (Android, iOS, Desktop, Web).
 * 
 * @param modifier Use this to set the exact size of the canvas you want to capture (e.g., Modifier.size(128.dp))
 * @param content The UI you want to draw and capture.
 * @return A Painter containing the rendered image, or null if it's still rendering.
 */
@Composable
fun rememberPainterFromComposable(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
): Painter? {
    val graphicsLayer = rememberGraphicsLayer()
    var capturedPainter by remember { mutableStateOf<Painter?>(null) }

    // This Box sits in your UI tree to measure the layout, but intercepts 
    // the drawing phase so it never actually appears on screen.
    Box(
        modifier = modifier.drawWithContent {
            // Record the visual output into the graphics layer
            graphicsLayer.record {
                this@drawWithContent.drawContent()
            }
            // By NOT calling drawContent() here, the Box remains completely invisible to the user
        }
    ) {
        content()
    }

    // Extract the bitmap as soon as the UI is laid out and recorded
    LaunchedEffect(graphicsLayer) {
        val bitmap = graphicsLayer.toImageBitmap()
        capturedPainter = BitmapPainter(bitmap)
    }

    return capturedPainter
}