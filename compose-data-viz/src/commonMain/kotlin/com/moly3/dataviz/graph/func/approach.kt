package com.moly3.dataviz.graph.func

import kotlin.math.max
import kotlin.math.min

fun approach(current: Float, target: Float, rate: Float, dtSec: Float): Float {
    if (current == target) return target
    val step = rate * dtSec
    return if (current < target) min(current + step, target)
    else max(current - step, target)
}