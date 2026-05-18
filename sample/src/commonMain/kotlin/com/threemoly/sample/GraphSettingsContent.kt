package com.threemoly.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.moly3.dataviz.core.graph.hull.GroupSettings
import com.moly3.dataviz.core.graph.model.GraphEdgeSettings
import com.moly3.dataviz.core.graph.model.GraphSelectionSettings
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.core.graph.model.GraphTextSettings
import com.moly3.dataviz.core.graph.model.GraphTheme
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.dataviz.core.graph.model.GraphWatchSettings
import com.moly3.dataviz.core.graph.model.GraphZoomSettings
import com.threemoly.sample.base.uikit.ObsText
import kotlin.math.roundToInt

@Composable
fun GraphSettingsContent(
    settings: GraphSettings,
    onChange: (GraphSettings) -> Unit,
    zoom: Float,
    nodeCount: Int,
    onNodeCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ObsText("zoom: %.3f".format(zoom))
        IntSliderRow(
            "Nodes count", nodeCount, valueRange = 1 until 20_000) {
            onNodeCountChange(it)
        }
        SettingsSection(title = "Theme", accentColor = Color(0xFF7E57C2)) {
            ThemeSection(
                theme = settings.theme,
                onChange = { onChange(settings.copy(theme = it)) },
            )
        }

        SettingsSection(title = "Selection & Hover", accentColor = Color(0xFFAB47BC)) {
            SelectionSection(
                selection = settings.selection,
                onChange = { onChange(settings.copy(selection = it)) },
            )
        }

        SettingsSection(title = "Edges", accentColor = Color(0xFF42A5F5)) {
            EdgeSection(
                edge = settings.edge,
                onChange = { onChange(settings.copy(edge = it)) },
            )
        }

        SettingsSection(title = "Text", accentColor = Color(0xFF26A69A)) {
            TextSection(
                text = settings.text,
                onChange = { onChange(settings.copy(text = it)) },
            )
        }

        SettingsSection(title = "Zoom", accentColor = Color(0xFF66BB6A)) {
            ZoomSection(
                zoomSettings = settings.zoom,
                onChange = { onChange(settings.copy(zoom = it)) },
            )
        }

        SettingsSection(title = "Watch Ring", accentColor = Color(0xFFFFB300)) {
            WatchSection(
                watch = settings.watch,
                onChange = { onChange(settings.copy(watch = it)) },
            )
        }

        SettingsSection(
            title = "Physics — Forces",
            accentColor = Color(0xFFFF7043),
            initiallyExpanded = true
        ) {
            PhysicsForcesSection(
                view = settings.view,
                onChange = { onChange(settings.copy(view = it)) },
            )
        }

        SettingsSection(title = "Physics — Clustering & Limits", accentColor = Color(0xFFEF5350)) {
            PhysicsAdvancedSection(
                view = settings.view,
                onChange = { onChange(settings.copy(view = it)) },
            )
        }

        SettingsSection(title = "Groups & Hulls", accentColor = Color(0xFF8D6E63)) {
            GroupSection(
                groups = settings.groupSettings,
                onChange = { onChange(settings.copy(groupSettings = it)) },
            )
        }
    }
}

// =====================================================================================
// Theme
// =====================================================================================

@Composable
private fun ThemeSection(theme: GraphTheme, onChange: (GraphTheme) -> Unit) {
    ColorRow("Node color", theme.nodeColor) { onChange(theme.copy(nodeColor = it)) }
    ColorRow("Edge color", theme.resolvedEdgeColor) { onChange(theme.copy(edgeColor = it)) }
    ColorRow("Accent (selected)", theme.accentColor) { onChange(theme.copy(accentColor = it)) }
    ColorRow("Text color", theme.textColor) { onChange(theme.copy(textColor = it)) }
    ColorRow("Dragged node", theme.draggedNodeColor) { onChange(theme.copy(draggedNodeColor = it)) }
    ColorRow("Hovered node", theme.hoveredNodeColor) { onChange(theme.copy(hoveredNodeColor = it)) }
    ColorRow("Pill bg (dark)", theme.activeLabelBackgroundDark) {
        onChange(theme.copy(activeLabelBackgroundDark = it))
    }
    ColorRow("Pill bg (light)", theme.activeLabelBackgroundLight) {
        onChange(theme.copy(activeLabelBackgroundLight = it))
    }
}

// =====================================================================================
// Selection
// =====================================================================================

@Composable
private fun SelectionSection(
    selection: GraphSelectionSettings,
    onChange: (GraphSelectionSettings) -> Unit,
) {
    SliderRow("Scale on hover", selection.scaleOnHover, valueRange = 1f..4f) {
        onChange(selection.copy(scaleOnHover = it))
    }
    IntSliderRow("Scale anim (ms)", selection.scaleAnimationMs, valueRange = 0..1000) {
        onChange(selection.copy(scaleAnimationMs = it))
    }
    IntSliderRow(
        "Selection anim (ms)",
        selection.selectionActiveAnimationMs,
        valueRange = 0..1000
    ) {
        onChange(selection.copy(selectionActiveAnimationMs = it))
    }
    SliderRow("Faded node alpha", selection.fadedNodeAlpha, valueRange = 0f..1f) {
        onChange(selection.copy(fadedNodeAlpha = it))
    }
    SliderRow("Faded edge alpha", selection.fadedEdgeAlpha, valueRange = 0f..1f) {
        onChange(selection.copy(fadedEdgeAlpha = it))
    }
    SliderRow("Selected text alpha", selection.selectedTextAlpha, valueRange = 0f..1f) {
        onChange(selection.copy(selectedTextAlpha = it))
    }
    SliderRow("Unrelated text alpha", selection.unrelatedTextAlpha, valueRange = 0f..1f) {
        onChange(selection.copy(unrelatedTextAlpha = it))
    }
    SliderRow("Fade-in rate (/s)", selection.fadeInRatePerSec, valueRange = 0.5f..20f) {
        onChange(selection.copy(fadeInRatePerSec = it))
    }
    SliderRow("Fade-out rate (/s)", selection.fadeOutRatePerSec, valueRange = 0.5f..20f) {
        onChange(selection.copy(fadeOutRatePerSec = it))
    }
    SliderRow("Node dim rate (/s)", selection.nodeDimRatePerSec, valueRange = 0.5f..20f) {
        onChange(selection.copy(nodeDimRatePerSec = it))
    }
    SliderRow("Edge fade rate (/s)", selection.edgeFadeRatePerSec, valueRange = 0.5f..20f) {
        onChange(selection.copy(edgeFadeRatePerSec = it))
    }
}

// =====================================================================================
// Edges
// =====================================================================================

@Composable
private fun EdgeSection(edge: GraphEdgeSettings, onChange: (GraphEdgeSettings) -> Unit) {
    SliderRow("Stroke width", edge.strokeWidth, valueRange = 0.1f..5f) {
        onChange(edge.copy(strokeWidth = it))
    }
    SliderRow("Highlight bonus", edge.strokeHighlightBonus, valueRange = 0f..5f) {
        onChange(edge.copy(strokeHighlightBonus = it))
    }
    SliderRow("Visibility zoom min", edge.visibilityZoomThreshold, valueRange = 0f..1f) {
        onChange(edge.copy(visibilityZoomThreshold = it))
    }
}

fun String.format(value: Float): String {
    return value.toString()
}

// =====================================================================================
// Text
// =====================================================================================

@Composable
private fun TextSection(text: GraphTextSettings, onChange: (GraphTextSettings) -> Unit) {
    SliderRow(
        "Normal font size (sp)", text.normalFontSizeSp.sp.value, valueRange = 1f..32f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(text.copy(normalFontSizeSp = it))
    }
    SliderRow(
        "Active font size (px)", text.activeFontSizePx, valueRange = 12f..96f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(text.copy(activeFontSizePx = it))
    }
    SliderRow(
        "Label padding (dp)", text.labelPaddingDp, valueRange = 0f..64f,
        valueFormatter = { it.roundToInt().toString() }) {
        onChange(text.copy(labelPaddingDp = it))
    }
    IntSliderRow(
        "Max labels visible",
        text.maxLabelsVisible.coerceAtMost(500),
        valueRange = 0..500
    ) {
        onChange(text.copy(maxLabelsVisible = it))
    }
    SliderRow("Visibility zoom min", text.visibilityZoomThreshold, valueRange = 0f..2f) {
        onChange(text.copy(visibilityZoomThreshold = it))
    }
    SliderRow("Visibility fade width", text.visibilityZoomFadeWidth, valueRange = 0.01f..2f) {
        onChange(text.copy(visibilityZoomFadeWidth = it))
    }
    SliderRow(
        "Pill padding X", text.activePillPaddingX, valueRange = 0f..64f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(text.copy(activePillPaddingX = it))
    }
    SliderRow(
        "Pill padding Y", text.activePillPaddingY, valueRange = 0f..64f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(text.copy(activePillPaddingY = it))
    }
    SliderRow(
        "Pill corner radius", text.activePillCornerRadius, valueRange = 0f..64f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(text.copy(activePillCornerRadius = it))
    }
    SliderRow("Pill bg alpha", text.activePillBackgroundAlpha, valueRange = 0f..1f) {
        onChange(text.copy(activePillBackgroundAlpha = it))
    }
}

// =====================================================================================
// Zoom
// =====================================================================================

@Composable
private fun ZoomSection(zoomSettings: GraphZoomSettings, onChange: (GraphZoomSettings) -> Unit) {
    SliderRow("Min zoom", zoomSettings.minZoom, valueRange = 0.01f..1f) {
        onChange(zoomSettings.copy(minZoom = it))
    }
    SliderRow(
        "Max zoom", zoomSettings.maxZoom, valueRange = 1f..32f,
        valueFormatter = { "%.1f".format(it) }) {
        onChange(zoomSettings.copy(maxZoom = it))
    }
    SliderRow("Step in", zoomSettings.stepIn, valueRange = 1.001f..2f) {
        onChange(zoomSettings.copy(stepIn = it, stepOut = 1f / it))
    }
}

// =====================================================================================
// Watch
// =====================================================================================

@Composable
private fun WatchSection(watch: GraphWatchSettings, onChange: (GraphWatchSettings) -> Unit) {
    SliderRow("Radius multiplier", watch.radiusMultiplier, valueRange = 1f..4f) {
        onChange(watch.copy(radiusMultiplier = it))
    }
    SliderRow(
        "Stroke width", watch.strokeWidth, valueRange = 0.5f..16f,
        valueFormatter = { "%.1f".format(it) }) {
        onChange(watch.copy(strokeWidth = it))
    }
}

// =====================================================================================
// Physics — Forces
// =====================================================================================

@Composable
private fun PhysicsForcesSection(view: GraphViewSettings, onChange: (GraphViewSettings) -> Unit) {
    SliderRow(
        "Center force", view.centerForce, valueRange = 0.001f..1f,
        valueFormatter = { "%.4f".format(it) }) {
        onChange(view.copy(centerForce = it))
    }
    SliderRow(
        "Link force", view.linkForce, valueRange = 0.00001f..10f,
        valueFormatter = { "%.4f".format(it) }) {
        onChange(view.copy(linkForce = it))
    }
    SliderRow(
        "Link distance", view.linkDistance, valueRange = 1f..500f,
        valueFormatter = { "%.1f".format(it) }) {
        onChange(view.copy(linkDistance = it))
    }
    SliderRow(
        "Repel force", view.repelForce, valueRange = 0.1f..100_000f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(view.copy(repelForce = it))
    }
    SliderRow(
        "Circle size", view.circleSize, valueRange = 0.1f..50f,
        valueFormatter = { "%.1f".format(it) }) {
        onChange(view.copy(circleSize = it))
    }
    SliderRow("Circle size multiplier", view.circleSizeMultiplier ?: 0f, valueRange = 0f..5f) {
        onChange(view.copy(circleSizeMultiplier = if (it == 0f) null else it))
    }
    SliderRow(
        "Max force", view.maxForce, valueRange = 1f..100f,
        valueFormatter = { "%.1f".format(it) }) {
        onChange(view.copy(maxForce = it))
    }
    SliderRow("Damping", view.dampingFactor, valueRange = 0.5f..1f) {
        onChange(view.copy(dampingFactor = it))
    }
    SliderRow(
        "Node quality", view.circleQuality, valueRange = 0.01f..1f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(view.copy(circleQuality = it))
    }
}

// =====================================================================================
// Physics — Clustering & advanced
// =====================================================================================

@Composable
private fun PhysicsAdvancedSection(view: GraphViewSettings, onChange: (GraphViewSettings) -> Unit) {
    SliderRow("Connected repulsion ×", view.connectedRepulsionMultiplier, valueRange = 0f..5f) {
        onChange(view.copy(connectedRepulsionMultiplier = it))
    }
    SliderRow("Mutual repulsion ×", view.mutualConnectionRepulsionMultiplier, valueRange = 0f..5f) {
        onChange(view.copy(mutualConnectionRepulsionMultiplier = it))
    }
    SliderRow(
        "Unconnected repulsion ×",
        view.unconnectedRepulsionMultiplier,
        valueRange = 0.1f..10f
    ) {
        onChange(view.copy(unconnectedRepulsionMultiplier = it))
    }
    SliderRow(
        "Long-distance link ×", view.longDistanceLinkMultiplier, valueRange = 1f..1000f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(view.copy(longDistanceLinkMultiplier = it))
    }
    SliderRow(
        "Clustering force", view.clusteringForce, valueRange = 0f..50f,
        valueFormatter = { "%.1f".format(it) }) {
        onChange(view.copy(clusteringForce = it))
    }
    IntSliderRow(
        "Min mutual for cluster",
        view.minMutualConnectionsForClustering,
        valueRange = 1..20
    ) {
        onChange(view.copy(minMutualConnectionsForClustering = it))
    }
    IntSliderRow(
        "Max conn for full proc",
        view.maxConnectionsForFullProcessing,
        valueRange = 10..1000
    ) {
        onChange(view.copy(maxConnectionsForFullProcessing = it))
    }
    IntSliderRow("Spatial opt threshold", view.spatialOptimizationThreshold, valueRange = 10..500) {
        onChange(view.copy(spatialOptimizationThreshold = it))
    }
    IntSliderRow("Target frame (ms)", view.targetFrameMs.toInt(), valueRange = 4..100) {
        onChange(view.copy(targetFrameMs = it.toLong()))
    }
    SliderRow("hubExpansionExponent", view.hubExpansionExponent, valueRange = 0f..1f) {
        onChange(view.copy(hubExpansionExponent = it))
    }
}

// =====================================================================================
// Groups & Hulls
// =====================================================================================

@Composable
private fun GroupSection(groups: GroupSettings, onChange: (GroupSettings) -> Unit) {
    ToggleRow("Enabled", groups.enabled) {
        onChange(groups.copy(enabled = it))
    }

    // ---------- Forces ----------
    SliderRow(
        "Cohesion force", groups.cohesionForce, valueRange = 0f..5f,
        valueFormatter = { "%.3f".format(it) }) {
        onChange(groups.copy(cohesionForce = it))
    }
    SliderRow(
        "Group separation", groups.groupSeparation, valueRange = 0f..500_000f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(groups.copy(groupSeparation = it))
    }
    SliderRow(
        "Separation softening", groups.groupSeparationSoftening, valueRange = 1f..500f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(groups.copy(groupSeparationSoftening = it))
    }

    // ---------- Hull recompute ----------
    IntSliderRow(
        "Hull recompute (ms)", groups.hullRecomputeIntervalMs.toInt(),
        valueRange = 16..1000
    ) {
        onChange(groups.copy(hullRecomputeIntervalMs = it.toLong()))
    }
    IntSliderRow(
        "Hull settled (ms)", groups.hullSettledIntervalMs.toInt(),
        valueRange = 50..5000
    ) {
        onChange(groups.copy(hullSettledIntervalMs = it.toLong()))
    }

    // ---------- Hull shape ----------
    IntSliderRow("Hull K (smoothness)", groups.hullK, valueRange = 3..20) {
        onChange(groups.copy(hullK = it))
    }
    SliderRow(
        "Hull padding (px)", groups.hullPadding, valueRange = 0f..200f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(groups.copy(hullPadding = it))
    }
    SliderRow("Hull smoothing", groups.hullSmoothing, valueRange = 0f..1f) {
        onChange(groups.copy(hullSmoothing = it))
    }
    SliderRow(
        "Hull stroke width", groups.hullStrokeWidth, valueRange = 0f..20f,
        valueFormatter = { "%.1f".format(it) }) {
        onChange(groups.copy(hullStrokeWidth = it))
    }

    // ---------- Hull fill ----------
    ToggleRow("Hull fill", groups.hullFill) {
        onChange(groups.copy(hullFill = it))
    }
    SliderRow("Hull fill alpha", groups.hullFillAlpha, valueRange = 0f..1f) {
        onChange(groups.copy(hullFillAlpha = it))
    }

    // ---------- Hull labels ----------
    SliderRow(
        "Label zoom threshold", groups.hullLabelVisibilityZoomThreshold,
        valueRange = 0f..2f
    ) {
        onChange(groups.copy(hullLabelVisibilityZoomThreshold = it))
    }
    SliderRow(
        "Label fade width", groups.hullLabelVisibilityZoomFadeWidth,
        valueRange = 0.01f..2f
    ) {
        onChange(groups.copy(hullLabelVisibilityZoomFadeWidth = it))
    }
    SliderRow(
        "Label font size (sp)", groups.hullLabelFontSizeSp, valueRange = 6f..48f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(groups.copy(hullLabelFontSizeSp = it))
    }
    ToggleRow("Label scale with zoom", groups.hullLabelScaleWithZoom) {
        onChange(groups.copy(hullLabelScaleWithZoom = it))
    }
    SliderRow("Label min scale", groups.hullLabelMinScale, valueRange = 0.1f..2f) {
        onChange(groups.copy(hullLabelMinScale = it))
    }
    SliderRow("Label max scale", groups.hullLabelMaxScale, valueRange = 1f..10f) {
        onChange(groups.copy(hullLabelMaxScale = it))
    }
    SliderRow(
        "Label vertical offset", groups.hullLabelVerticalOffset, valueRange = -100f..100f,
        valueFormatter = { "%.0f".format(it) }) {
        onChange(groups.copy(hullLabelVerticalOffset = it))
    }
}