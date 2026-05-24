package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

enum class ArrowHead {
    None,
    Open,
    FilledTriangle,
    HollowTriangle,
    FilledDiamond,
    HollowDiamond,
}

enum class LineStyle {
    Solid,
    Dashed,
    Dotted,
}

@Serializable
data class ArrowStyle(
    val head: ArrowHead = ArrowHead.None,
    val line: LineStyle = LineStyle.Solid,
    @Serializable(with = ComposeColorSerializer::class)
    val color: Color = Color.Unspecified, // Unspecified -> fall back to theme.resolvedEdgeColor
) {
    companion object {
        val Default = ArrowStyle()
    }
}

@Immutable
@Serializable
data class Connection<Id>(
    val target: Id,
    val style: ArrowStyle = ArrowStyle.Default,
)

/** Convenience: build a plain undirected, default-styled connection. */
fun <Id> Id.asConnection(): Connection<Id> = Connection(this)