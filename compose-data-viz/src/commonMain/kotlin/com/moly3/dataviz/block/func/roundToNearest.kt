package com.moly3.dataviz.block.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.model.SaveableOffset
import kotlin.math.round

fun Float.roundToNearest(number: Int): Float {
    return (round(this / number) * number)
}

fun SaveableOffset.roundToNearest(number: Int?): SaveableOffset {
    if (number == null || number == 0)
        return this
    return this.copy(x = this.x.roundToNearest(number), y = this.y.roundToNearest(number))
}