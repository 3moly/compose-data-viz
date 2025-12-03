package com.moly3.dataviz.whiteboard.func

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

@Stable
fun Modifier.absoluteOffset(coords: Offset) =
    this then
            absoluteOffset(
                x = coords.x.dp,
                y = coords.y.dp
            )