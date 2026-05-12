package com.moly3.dataviz.core.graph.engine.impl.hybrid

internal object HybridMath {
    const val MAX_SAFE_VALUE = 1e5f
    const val MIN_DISTANCE = 0.01f

    @Suppress("NOTHING_TO_INLINE")
    inline fun safeFloat(value: Float): Float {
        return when {
            value.isNaN() -> 0f
            value > MAX_SAFE_VALUE -> MAX_SAFE_VALUE
            value < -MAX_SAFE_VALUE -> -MAX_SAFE_VALUE
            else -> value
        }
    }
}