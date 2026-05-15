package com.moly3.dataviz.graph.features.atlas

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.collections.immutable.ImmutableMap
import kotlin.time.Clock

@Immutable
data class AtlasState(
    val bitmap: ImageBitmap,
    val indexMap: ImmutableMap<String, Int>,
    val columns: Int,
    val tileSizePx: Int,
    val isCircular: Boolean = true,
    val version: Long = Clock.System.now().toEpochMilliseconds()
)
