package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import kotlin.math.round

fun Float.roundToNearest(number: Int): Float {
    return (round(this / number) * number)
}

fun Offset.roundToNearest( number: Int): Offset {
    return this.copy(x = this.x.roundToNearest(number), y = this.y.roundToNearest(number))
}