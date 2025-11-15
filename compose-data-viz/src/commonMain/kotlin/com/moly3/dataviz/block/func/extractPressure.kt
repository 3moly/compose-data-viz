package com.moly3.dataviz.block.func

import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType

fun extractPressure(change: PointerInputChange): Float {
    return when (change.type) {
        PointerType.Stylus -> {
            // Extract pressure from stylus input
            change.pressure.takeIf { it > 0f } ?: 0.8f
        }

        else -> 0.6f // Default pressure for touch
    }
}

fun extractTilt(change: PointerInputChange): Pair<Float, Float> {
    return when (change.type) {
        PointerType.Stylus -> {
            // Extract tilt information if available
            val tiltX = change.historical.lastOrNull()?.position?.x?.let { 0f } ?: 0f
            val tiltY = change.historical.lastOrNull()?.position?.y?.let { 0f } ?: 0f
            Pair(tiltX, tiltY)
        }

        else -> Pair(0f, 0f)
    }
}