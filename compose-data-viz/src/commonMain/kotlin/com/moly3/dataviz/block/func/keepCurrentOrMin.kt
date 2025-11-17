package com.moly3.dataviz.block.func

import com.moly3.dataviz.block.model.SaveableOffset

fun SaveableOffset.keepCurrentOrMin(minSize: Float): SaveableOffset {
    return this.copy(
        x = if (minSize <= x) x else minSize,
        y = if (minSize <= y) y else minSize,
    )
}