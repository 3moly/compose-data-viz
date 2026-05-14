package com.moly3.dataviz.graph.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.func.half
import com.moly3.dataviz.graph.func.getNodeConnections
import kotlin.math.max
import kotlin.math.min

@Composable
fun <Id, Data> GraphInternal2(
    modifier: Modifier = Modifier,
    settings: GraphSettings,
    atlas: AtlasState? = null,
    getIconIndex: (Id, Data) -> Int? = { _, _ -> null },
    getNodeGroups: (Id, Data) -> List<String> = { _, _ -> emptyList() },
    // NEW: Map a group ID to a color. Use transparency (e.g., alpha = 0.3f)
    getGroupColor: (String) -> Color = { Color(0x4D00BFFF) },
    nodes: List<GraphNode<Id, Data>>,
    connections: Map<Id, List<Id>>,
    coordinates: Map<Id, Offset>,
    coordinatesVersion: Int, // Drawing trigger
    draggedNodeId: Id?,
    cursorNodeId: Id?,
    watchNodeId: Id?,
    movementOffset: Offset,
    zoom: Float,
    customPopup: (@Composable (node: GraphNode<Id, Data>) -> Unit)? = null
) {

    val theme = settings.theme
    val view = settings.view
    val selectionCfg = settings.selection
    val edgeCfg = settings.edge
    val textCfg = settings.text
    val watchCfg = settings.watch
    val baseTextStyle = settings.textStyle

    val circleRadius = view.circleSize
    val circleSizeMultiplier = view.circleSizeMultiplier
    val maxTextsAtCenterVisible = textCfg.maxLabelsVisible

    val animZoom = zoom
    val localDensity = LocalDensity.current
    val textPadding = remember(textCfg.labelPaddingDp) {
        localDensity.run { textCfg.labelPaddingDp.toDp().toPx() }
    }

    val textMeasurer = rememberTextMeasurer()
    val textMeasurerNoCaching = rememberTextMeasurer()

    val visibleTextsPool = remember { ArrayList<VisibleTextData>() } // Object Pool
    val nodeById = remember(nodes) { nodes.associateBy { it.id } }

    val textSignature = remember(nodes) {
        var h = nodes.size
        for (i in nodes.indices) {
            val n = nodes[i]
            h = h * 31 xor n.id.hashCode()
            h = h * 31 xor n.name.hashCode()
        }
        h
    }

    val textLayouts = remember(textSignature, baseTextStyle, textCfg.normalFontSize) {
        val style = baseTextStyle.copy(fontSize = textCfg.normalFontSize)
        nodes.associate { node ->
            node.id to textMeasurer.measure(text = node.name, style = style)
        }
    }

    val activeNodeId: Id? = draggedNodeId ?: cursorNodeId

    val activeNodeTextLayout = remember(
        activeNodeId, nodes, baseTextStyle, textCfg.activeFontSizePx, localDensity.density
    ) {
        val style = baseTextStyle.copy(
            fontSize = (textCfg.activeFontSizePx / localDensity.density).sp
        )
        activeNodeId?.let { id ->
            nodeById[id]?.let { textMeasurerNoCaching.measure(text = it.name, style = style) }
        }
    }

    val activeConnectionSet = remember(activeNodeId, connections) {
        if (activeNodeId != null) connections.getNodeConnections(activeNodeId).toHashSet()
        else emptySet()
    }

    // Use a HashMap for random lookups, but map it to an Array for O(1) iterations
    val nodeAnimStates = remember { HashMap<Id, NodeAnimState>() }
    val animStatesList = remember(nodes) {
        val currentIds = nodes.mapTo(HashSet()) { it.id }
        nodeAnimStates.keys.retainAll(currentIds)

        // Cache list maps directly to nodes.indices for instantaneous lookup inside Loops
        Array(nodes.size) { i ->
            nodeAnimStates.getOrPut(nodes[i].id) { NodeAnimState() }
        }
    }

    var cursorTextAlpha by remember { mutableStateOf(0f) }
    var animTick by remember { mutableStateOf(0) }

    val latestActiveNodeId by rememberUpdatedState(activeNodeId)
    val latestActiveConnectionSet by rememberUpdatedState(activeConnectionSet)
    val latestNodes by rememberUpdatedState(nodes)
    val latestSelectionCfg by rememberUpdatedState(selectionCfg)
    val latestAnimStatesList by rememberUpdatedState(animStatesList)


    val groupToNodesMap = remember(nodes, getNodeGroups) {
        val map = mutableMapOf<String, MutableList<Id>>()
        for (node in nodes) {
            val groups = getNodeGroups(node.id, node.data)
            for (group in groups) {
                map.getOrPut(group) { mutableListOf() }.add(node.id)
            }
        }
        map
    }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val dtSec = if (lastNanos == 0L) 1f / 60f
                else ((nowNanos - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
                lastNanos = nowNanos

                val cfg = latestSelectionCfg
                val currentActiveId = latestActiveNodeId
                val currentNodes = latestNodes
                val currentConnections = latestActiveConnectionSet
                val currentStatesList = latestAnimStatesList

                val hasSelection = currentActiveId != null
                var anyChange = false

                val rateActive = 1000f / cfg.scaleAnimationMs.coerceAtLeast(1)

                // High-performance loop utilizing O(1) state lookup
                for (i in currentNodes.indices) {
                    val node = currentNodes[i]
                    val state = currentStatesList[i]

                    val targetText: Float
                    val targetDim: Float
                    val targetActive = if (node.id == currentActiveId) 1f else 0f

                    when {
                        !hasSelection -> {
                            targetText = 1f; targetDim = 0f
                        }

                        node.id == currentActiveId -> {
                            targetText = 0f; targetDim = 0f
                        }

                        node.id in currentConnections -> {
                            targetText = cfg.selectedTextAlpha; targetDim = 0f
                        }

                        else -> {
                            targetText = cfg.unrelatedTextAlpha; targetDim = 1f
                        }
                    }

                    val rateText = if (targetText > state.textAlpha)
                        cfg.fadeInRatePerSec else cfg.fadeOutRatePerSec

                    val newText = approach(state.textAlpha, targetText, rateText, dtSec)
                    val newDim = approach(state.dimFactor, targetDim, cfg.nodeDimRatePerSec, dtSec)
                    val newActive = approach(state.activeKoef, targetActive, rateActive, dtSec)

                    if (newText != state.textAlpha || newDim != state.dimFactor || newActive != state.activeKoef) {
                        state.textAlpha = newText
                        state.dimFactor = newDim
                        state.activeKoef = newActive
                        anyChange = true
                    }
                }

                val cursorTarget = if (hasSelection) 1f else 0f
                val cursorRate = if (cursorTarget > cursorTextAlpha)
                    cfg.fadeInRatePerSec else cfg.fadeOutRatePerSec
                val newCursor = approach(cursorTextAlpha, cursorTarget, cursorRate, dtSec)
                if (newCursor != cursorTextAlpha) {
                    cursorTextAlpha = newCursor
                    anyChange = true
                }

                if (anyChange) animTick++
            }
        }
    }

    val drawText = animZoom > textCfg.visibilityZoomThreshold
    val drawEdges = animZoom > edgeCfg.visibilityZoomThreshold

    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.onSizeChanged { boxSize = it }) {
        Canvas(modifier = Modifier.matchParentSize()) {
            @Suppress("UNUSED_EXPRESSION") animTick
            @Suppress("UNUSED_EXPRESSION") coordinatesVersion // Forces Canvas redraw when map triggers

            val canvasW = size.width
            val canvasH = size.height
            val centerX = canvasW * 0.5f
            val centerY = canvasH * 0.5f

            val invZoom = 1f / animZoom.coerceAtLeast(0.0001f)
            val cullPad = 100f
            val cullL = (-centerX) * invZoom - movementOffset.x - cullPad
            val cullR = (canvasW - centerX) * invZoom - movementOffset.x + cullPad
            val cullT = (-centerY) * invZoom - movementOffset.y - cullPad
            val cullB = (canvasH - centerY) * invZoom - movementOffset.y + cullPad

            val solidBackgroundColor = Color(0xFF121212)
            val fadedNodeAlpha = selectionCfg.fadedNodeAlpha
            val fadedEdgeAlpha = selectionCfg.fadedEdgeAlpha

            withTransform({
                scale(animZoom, animZoom)
                translate(center.x + movementOffset.x, center.y + movementOffset.y)
            }) {
                // 1. DRAW PUDDLES (Directories / Tags)
                // We draw this first so it sits beneath edges and nodes.
                val groupPositions = mutableMapOf<String, MutableList<Offset>>()
                for (node in nodes) {
                    val pos = coordinates[node.id] ?: continue
                    // Cull check: only process groups if at least one node is visible
                    if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue

                    val groups = getNodeGroups(node.id, node.data)
                    groups.forEach { group ->
                        groupPositions.getOrPut(group) { mutableListOf() }.add(pos)
                    }
                }

                val puddlePadding = 45f // Minimum distance from node to puddle edge
                groupPositions.forEach { (groupId, positions) ->
                    if (positions.isEmpty()) return@forEach

                    val color = getGroupColor(groupId)

                    // Calculate the "Center of the Drop"
                    var sumX = 0f
                    var sumY = 0f
                    positions.forEach { sumX += it.x; sumY += it.y }
                    val centroid = Offset(sumX / positions.size, sumY / positions.size)

                    // Draw the fluid body
                    if (positions.size > 1) {
                        positions.forEach { nodePos ->
                            // Draw a thick rounded "bridge" from centroid to node
                            drawLine(
                                color = color,
                                start = centroid,
                                end = nodePos,
                                strokeWidth = puddlePadding * 2.2f, // Thickness of the "water"
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Draw the "Surface" around each node
                    positions.forEach { nodePos ->
                        drawCircle(
                            color = color,
                            radius = puddlePadding,
                            center = nodePos
                        )
                    }
                }

                // 1. Draw Edges First (so they sit underneath nodes)
                if (drawEdges) {
                    val strokeNormal = edgeCfg.strokeWidth / animZoom
                    val strokeHighlight =
                        (edgeCfg.strokeWidth + edgeCfg.strokeHighlightBonus) / animZoom
                    val baseEdgeColor = theme.resolvedEdgeColor

                    for (i in nodes.indices) {
                        val sId = nodes[i].id
                        val sPos = coordinates[sId] ?: continue
                        val conns = connections[sId] ?: continue

                        for (j in conns.indices) {
                            val tId = conns[j]
                            val tPos = coordinates[tId] ?: continue

                            val minX = min(sPos.x, tPos.x);
                            val maxX = max(sPos.x, tPos.x)
                            val minY = min(sPos.y, tPos.y);
                            val maxY = max(sPos.y, tPos.y)
                            if (maxX < cullL || minX > cullR || maxY < cullT || minY > cullB) continue

                            val sActive = animStatesList[i].activeKoef
                            val tActive = nodeAnimStates[tId]?.activeKoef ?: 0f
                            val maxActive = max(sActive, tActive)

                            val stroke = strokeNormal + (strokeHighlight - strokeNormal) * maxActive

                            val dimmedLine = lerp(
                                baseEdgeColor,
                                baseEdgeColor.copy(alpha = fadedEdgeAlpha),
                                if (activeNodeId != null) 1f else 0f
                            )
                            val edgeColor = lerp(dimmedLine, theme.accentColor, maxActive)

                            drawLine(
                                color = edgeColor,
                                start = sPos,
                                end = tPos,
                                strokeWidth = stroke
                            )
                        }
                    }
                }

                // 2. Draw Nodes (Standard Canvas Operations)
                for (i in nodes.indices) {
                    val node = nodes[i]
                    val pos = coordinates[node.id] ?: continue
                    if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue

                    val baseRadius = GraphNode.getCircleSize(
                        circleRadius = circleRadius,
                        connectionCount = connections[node.id]?.size ?: 1,
                        multiplier = circleSizeMultiplier
                    )

                    val state = animStatesList[i]
                    val dim = state.dimFactor
                    val activeKoef = state.activeKoef

                    val r = baseRadius * lerp(1f, selectionCfg.scaleOnHover, activeKoef)

                    val iconIndex = getIconIndex(node.id, node.data) ?: -1
                    val hasIcon = iconIndex >= 0 && atlas != null

                    val baseColor = node.colorValue?.let { Color(it) } ?: theme.nodeColor
                    val hoverOrDragColor =
                        if (node.id == draggedNodeId) theme.draggedNodeColor else theme.hoveredNodeColor

                    val base = if (activeKoef > 0f) {
                        lerp(baseColor, hoverOrDragColor, activeKoef)
                    } else {
                        baseColor
                    }

                    val nodeColor = if (dim > 0f) {
                        lerp(base, solidBackgroundColor, dim * (1f - fadedNodeAlpha))
                    } else {
                        base
                    }

                    if (!hasIcon) {
                        // Regular Circle Draw
                        drawCircle(
                            color = nodeColor,
                            radius = r,
                            center = pos
                        )

                        // Replicate standard border from original shader
                        if (view.circleBorderWidth > 0f) {
                            val strokeW = view.circleBorderWidth * r
                            drawCircle(
                                color = Color(0xFF999999), // equivalent to original half4(0.6,0.6,0.6,1.0)
                                radius = r - strokeW / 2f,
                                center = pos,
                                style = Stroke(width = strokeW)
                            )
                        }
                    } else if (atlas != null) {
                        // Image Atlas Rect Draw
                        val iconAlpha = lerp(1f, fadedNodeAlpha, dim)
                        val atlasCols = atlas.columns
                        val atlasTileSize = atlas.tileSizePx

                        val texU = (iconIndex % atlasCols) * atlasTileSize
                        val texV = (iconIndex / atlasCols) * atlasTileSize

                        // We translate to the target and scale inside to prevent rounding IntOffset errors on deep zoom
                        translate(pos.x - r, pos.y - r) {
                            val targetScale = (2 * r) / atlasTileSize
                            scale(scaleX = targetScale, scaleY = targetScale, pivot = Offset.Zero) {
                                drawImage(
                                    image = atlas.bitmap,
                                    srcOffset = IntOffset(texU, texV),
                                    srcSize = IntSize(atlasTileSize, atlasTileSize),
                                    alpha = iconAlpha
                                )
                            }
                        }
                    }
                }

                // 3. Draw Highlights and Focus Elements
                if (watchNodeId != null) {
                    val watchPos = coordinates[watchNodeId]
                    if (watchPos != null) {
                        val watchRadius = GraphNode.getCircleSize(
                            circleRadius, connections[watchNodeId]?.size ?: 1, circleSizeMultiplier
                        )
                        drawCircle(
                            color = theme.accentColor,
                            radius = watchRadius * watchCfg.radiusMultiplier,
                            center = watchPos,
                            style = Stroke(width = watchCfg.strokeWidth / animZoom)
                        )
                    }
                }

                drawCircle(color = theme.accentColor, radius = 1f / animZoom, center = Offset.Zero)
            }

            // 4. Draw Distant Text Labels
            if (drawText) {
                var visibleTextCount = 0

                for (i in nodes.indices) {
                    val node = nodes[i]
                    if (activeNodeId == node.id) continue
                    val pos = coordinates[node.id] ?: continue
                    if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue

                    val alpha = animStatesList[i].textAlpha
                    if (alpha < 0.01f) continue

                    val screenX = (pos.x + movementOffset.x) * animZoom + centerX
                    val screenY = (pos.y + movementOffset.y) * animZoom + centerY

                    val dxs = screenX - centerX
                    val dys = screenY - centerY
                    val distSq = dxs * dxs + dys * dys

                    if (visibleTextCount >= visibleTextsPool.size) {
                        visibleTextsPool.add(VisibleTextData())
                    }
                    val data = visibleTextsPool[visibleTextCount++]
                    data.nodeIndex = i
                    data.distSq = distSq
                    data.screenX = screenX
                    data.screenY = screenY
                }

                val activeVisibleTexts = visibleTextsPool.subList(0, visibleTextCount)
                activeVisibleTexts.sortBy { it.distSq }

                val limit = min(visibleTextCount, maxTextsAtCenterVisible)
                val zoomFadeStart = textCfg.visibilityZoomThreshold
                val zoomFadeWidth = textCfg.visibilityZoomFadeWidth

                for (k in 0 until limit) {
                    val textData = activeVisibleTexts[k]
                    val nodeIndex = textData.nodeIndex
                    val screenPos = Offset(textData.screenX, textData.screenY)

                    val node = nodes[nodeIndex]
                    val layout = textLayouts[node.id] ?: continue

                    val nodeRadius = GraphNode.getCircleSize(
                        circleRadius, connections[node.id]?.size ?: 1, circleSizeMultiplier
                    )
                    val nodeTextAlpha = animStatesList[nodeIndex].textAlpha
                    val zoomAlpha = ((animZoom - zoomFadeStart) / zoomFadeWidth).coerceIn(0f, 1f)
                    val finalAlpha = (nodeTextAlpha * zoomAlpha).coerceIn(0f, 1f)
                    if (finalAlpha < 0.01f) continue

                    val pivotX = screenPos.x
                    val pivotY = screenPos.y + nodeRadius * animZoom + textPadding
                    val textTopLeft = Offset(pivotX - layout.size.width / 2f, pivotY)

                    val textScale = if (textCfg.scaleLabelsWithZoom) {
                        animZoom.coerceIn(textCfg.minLabelScale, textCfg.maxLabelScale)
                    } else {
                        1f
                    }

                    if (textScale != 1f) {
                        withTransform({
                            scale(
                                scaleX = textScale,
                                scaleY = textScale,
                                pivot = Offset(pivotX, pivotY)
                            )
                        }) {
                            drawText(
                                textLayoutResult = layout,
                                topLeft = textTopLeft,
                                color = theme.textColor,
                                alpha = finalAlpha
                            )
                        }
                    } else {
                        drawText(
                            textLayoutResult = layout,
                            topLeft = textTopLeft,
                            color = theme.textColor,
                            alpha = finalAlpha
                        )
                    }
                }
            }

            // 5. Draw Active Node Pill
            if (customPopup == null && activeNodeId != null && activeNodeTextLayout != null && cursorTextAlpha > 0.01f) {
                val activePos = coordinates[activeNodeId]
                if (activePos != null) {
                    val nodeRadius = GraphNode.getCircleSize(
                        circleRadius, connections[activeNodeId]?.size ?: 1, circleSizeMultiplier
                    )

                    val bgPadX = textCfg.activePillPaddingX
                    val bgPadY = textCfg.activePillPaddingY

                    val activeKoef = nodeAnimStates[activeNodeId]?.activeKoef ?: 1f
                    val activeScale = lerp(1f, selectionCfg.scaleOnHover, activeKoef)

                    val screenX = (activePos.x + movementOffset.x) * animZoom + centerX
                    val screenY = (activePos.y + movementOffset.y) * animZoom + centerY +
                            (nodeRadius * activeScale * animZoom + textPadding + bgPadY +
                                    (activeNodeTextLayout.size.height / 2f))

                    val textTopLeft = Offset(screenX, screenY) - activeNodeTextLayout.half()

                    val pillBgColor = if (theme.textColor.luminance() > 0.5f)
                        theme.activeLabelBackgroundDark else theme.activeLabelBackgroundLight

                    drawRoundRect(
                        color = pillBgColor.copy(alpha = textCfg.activePillBackgroundAlpha * cursorTextAlpha),
                        topLeft = Offset(textTopLeft.x - bgPadX, textTopLeft.y - bgPadY),
                        size = Size(
                            width = activeNodeTextLayout.size.width + bgPadX * 2f,
                            height = activeNodeTextLayout.size.height + bgPadY * 2f
                        ),
                        cornerRadius = CornerRadius(
                            textCfg.activePillCornerRadius,
                            textCfg.activePillCornerRadius
                        )
                    )

                    drawText(
                        textLayoutResult = activeNodeTextLayout,
                        topLeft = textTopLeft,
                        color = theme.textColor,
                        alpha = cursorTextAlpha
                    )
                }
            }
        }

        if (customPopup != null && activeNodeId != null && cursorTextAlpha > 0.01f) {
            val activeNode = nodeById[activeNodeId]
            val activePos = coordinates[activeNodeId]

            if (activeNode != null && activePos != null) {
                val centerX = boxSize.width * 0.5f
                val centerY = boxSize.height * 0.5f

                val nodeRadius = GraphNode.getCircleSize(
                    circleRadius, connections[activeNodeId]?.size ?: 1, circleSizeMultiplier
                )

                val activeKoef = nodeAnimStates[activeNodeId]?.activeKoef ?: 1f
                val activeScale = lerp(1f, selectionCfg.scaleOnHover, activeKoef)

                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = cursorTextAlpha }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)

                            val screenX = (activePos.x + movementOffset.x) * animZoom + centerX
                            val screenY = (activePos.y + movementOffset.y) * animZoom + centerY +
                                    (nodeRadius * activeScale * animZoom) + textPadding

                            layout(placeable.width, placeable.height) {
                                placeable.place(
                                    x = (screenX - placeable.width / 2f).toInt(),
                                    y = screenY.toInt()
                                )
                            }
                        }
                ) {
                    customPopup(activeNode)
                }
            }
        }
    }
}