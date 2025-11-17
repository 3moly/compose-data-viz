package com.moly3.dataviz.block.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
data class ConnectionConfig(
    val stubLength: Float,
    val controlPointFactor: Float,
    val maxArcHeight: Float,
    val hitThreshold: Dp = 50.dp,
    val strokeWidth: Dp = 2.dp,
    val arrowHeadSize: Dp = 10.dp,
)