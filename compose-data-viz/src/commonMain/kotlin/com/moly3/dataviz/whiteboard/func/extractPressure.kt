package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType

private const val DEFAULT_PRESSURE = 0.6f

fun extractPressure(change: PointerInputChange): Float =
    when (change.type) {
        PointerType.Stylus -> {
            // Extract pressure from stylus input
            change.pressure.takeIf { it > 0f } ?: DEFAULT_PRESSURE
        }

        else -> {
            DEFAULT_PRESSURE
        }
    }

fun extractTilt(change: PointerInputChange): Pair<Float, Float> =
    when (change.type) {
        PointerType.Stylus -> {
            // Extract tilt information if available
            val tiltX =
                change.historical
                    .lastOrNull()
                    ?.position
                    ?.x
                    ?.let { 0f } ?: 0f
            val tiltY =
                change.historical
                    .lastOrNull()
                    ?.position
                    ?.y
                    ?.let { 0f } ?: 0f
            Pair(tiltX, tiltY)
        }

        else -> {
            Pair(0f, 0f)
        }
    }
