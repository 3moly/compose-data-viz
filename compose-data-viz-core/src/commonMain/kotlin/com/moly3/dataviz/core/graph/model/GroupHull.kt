package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.moly3.dataviz.core.graph.model.GroupId

/** Computed hull geometry for one group at one point in time. */
@Immutable
data class GroupHull(
    val groupId: GroupId,
    val label: String, // copied from GroupHullDef.name at compute time
    val color: Color, // copied from GroupHullDef.color
    val path: Path,
    val labelAnchor: Offset,
)
