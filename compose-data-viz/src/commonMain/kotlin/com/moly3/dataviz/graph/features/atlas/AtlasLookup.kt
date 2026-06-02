package com.moly3.dataviz.graph.features.atlas

import androidx.compose.runtime.Immutable

@Immutable
data class AtlasLookup(
    val layerIndex: Int,
    val tileIndex: Int,
)
