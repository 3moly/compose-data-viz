package com.moly3.dataviz.graph.features.atlas

import androidx.compose.runtime.Immutable
import com.moly3.dataviz.graph.ui.TierSelection

@Immutable
data class AtlasTier(
    val name: String,
    val tileSizePx: Int,
    val selection: TierSelection,
    val isCircular: Boolean = false,
    val freezeOnMove: Boolean = false,
)
