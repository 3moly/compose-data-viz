package com.moly3.dataviz.graph.features.atlas

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal class NodeStaticData(
    val baseRadius: Float,
    val baseColor: Color,
    val iconLookup: AtlasLookup?,
)
