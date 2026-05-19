package com.moly3.dataviz.graph.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.moly3.dataviz.core.graph.hull.GroupHull
import com.moly3.dataviz.core.graph.hull.GroupSettings
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.func.half
import com.moly3.dataviz.graph.features.atlas.AtlasLayers
import com.moly3.dataviz.graph.features.atlas.NodeStaticData
import com.moly3.dataviz.graph.func.approach
import com.moly3.dataviz.graph.func.getNodeConnections
import com.moly3.shaders.buildEffect
import com.moly3.shaders.drawVertices2
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun <Id, Data> GraphInternal(
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
    settings: GraphSettings,
    atlasLayers: AtlasLayers = AtlasLayers.EMPTY,
    getIconKey: (Id, Data) -> String? = { _, _ -> null },
    nodes: List<GraphNode<Id, Data>>,
    connections: Map<Id, List<Id>>,
    coordinates: Map<Id, Offset>,
    coordinatesVersion: Int,
    draggedNodeId: Id?,
    cursorNodeId: Id?,
    watchNodeId: Id?,

    hulls: ImmutableList<GroupHull>,
    groupSettings: GroupSettings,

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
    val baseTextStyle = textStyle

    val circleRadius = view.circleSize
    val circleSizeMultiplier = view.circleSizeMultiplier
    val maxTextsAtCenterVisible = textCfg.maxLabelsVisible

    val layerCount = atlasLayers.layers.size
    val fallbackBitmap = remember { ImageBitmap(1, 1) }
    val shader = remember(layerCount) { GraphShader(layerCount) }

    val runtimeEffect = remember(shader) {
        buildEffect(shader)
    }
    val rtShader = remember(
        runtimeEffect,
        view.circleQuality,
        view.circleBorderWidth,
        atlasLayers.combinedVersion,
        fallbackBitmap
    ) {
        runtimeEffect.apply {
            setFloatUniform("uQuality", view.circleQuality)
            setFloatUniform("uBorderWidth", view.circleBorderWidth)
            setFloatUniform("uUseAtlas", if (layerCount > 0) 1f else 0f)
            setFloatUniform("uLayerCount", layerCount.toFloat())
            for (i in 0 until layerCount) {
                val layer = atlasLayers.layers[i]
                setImageUniform("uAtlas$i", layer.bitmap)
                setFloatUniform("uTileSize$i", layer.tileSizePx.toFloat())
                setFloatUniform("uColumns$i", layer.columns.toFloat())
                setFloatUniform("uCircular$i", if (layer.isCircular) 1f else 0f)
            }
        }.buildShader()
    }

    val animZoom = zoom
    val localDensity = LocalDensity.current
    val textPadding = remember(textCfg.labelPaddingDp) {
        localDensity.run { textCfg.labelPaddingDp.toDp().toPx() }
    }
    val textMeasurer = rememberTextMeasurer()
    val textMeasurerNoCaching = rememberTextMeasurer()

    val buffers = remember { GraphBuffers() }
    val visibleTextsPool = remember { ArrayList<VisibleTextData>() }
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

    val textLayouts = remember(textSignature, baseTextStyle, textCfg.normalFontSizeSp) {
        val style =
            baseTextStyle.copy(fontSize = textCfg.normalFontSizeSp.sp, textAlign = TextAlign.Center)
        nodes.associate { node ->
            node.id to textMeasurer.measure(
                text = node.name,
                maxLines = textCfg.labelMaxLines,
                constraints = Constraints(maxWidth = textCfg.labelMaxWidth),
                style = style
            )
        }
    }

    // Cache hull label layouts so we don't re-measure every frame.
    val hullLabelSignature = remember(hulls) {
        var h = hulls.size
        for (i in hulls.indices) {
            val hull = hulls[i]
            h = h * 31 xor hull.groupId.hashCode()
            h = h * 31 xor hull.label.hashCode()   // label IS in the signature
        }
        h
    }

    val hullLabelLayouts =
        remember(hullLabelSignature, baseTextStyle, groupSettings.hullLabelFontSizeSp) {
            val style = baseTextStyle.copy(fontSize = groupSettings.hullLabelFontSizeSp.sp)
            hulls.associate { hull ->
                hull.groupId to textMeasurer.measure(text = hull.label, style = style)
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

    val nodeAnimStates = remember { HashMap<Id, NodeAnimState>() }
    remember(nodes) {
        val currentIds = nodes.mapTo(HashSet()) { it.id }
        val it = nodeAnimStates.keys.iterator()
        while (it.hasNext()) if (it.next() !in currentIds) it.remove()
    }

    // Pre-calculate static visual data for nodes so we don't recalculate it inside Canvas every frame
    val staticNodeData =
        remember(nodes, connections, atlasLayers, theme, circleRadius, circleSizeMultiplier) {
            Array(nodes.size) { i ->
                val node = nodes[i]
                val connCount = connections[node.id]?.size ?: 1
                val radius = GraphNode.getCircleSize(circleRadius, connCount, circleSizeMultiplier)
                val color = node.colorValue?.let { Color(it) } ?: theme.nodeColor
                val icon = if (!atlasLayers.isEmpty) {
                    getIconKey(node.id, node.data)?.let { key -> atlasLayers.resolve(key) }
                } else null
                NodeStaticData(baseRadius = radius, baseColor = color, iconLookup = icon)
            }
        }

    var cursorTextAlpha by remember { mutableStateOf(0f) }
    var animTick by remember { mutableStateOf(0) }

    val latestActiveNodeId by rememberUpdatedState(activeNodeId)
    val latestActiveConnectionSet by rememberUpdatedState(activeConnectionSet)
    val latestNodes by rememberUpdatedState(nodes)
    val latestSelectionCfg by rememberUpdatedState(selectionCfg)

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

                val hasSelection = currentActiveId != null
                var anyChange = false

                val rateActive = 1000f / cfg.scaleAnimationMs.coerceAtLeast(1)

                for (i in currentNodes.indices) {
                    val node = currentNodes[i]
                    val state = nodeAnimStates.getOrPut(node.id) { NodeAnimState() }

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

                    val rateText =
                        if (targetText > state.textAlpha) cfg.fadeInRatePerSec else cfg.fadeOutRatePerSec
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
                val cursorRate =
                    if (cursorTarget > cursorTextAlpha) cfg.fadeInRatePerSec else cfg.fadeOutRatePerSec
                val newCursor = approach(cursorTextAlpha, cursorTarget, cursorRate, dtSec)
                if (newCursor != cursorTextAlpha) {
                    cursorTextAlpha = newCursor
                    anyChange = true
                }

                if (anyChange) animTick++
            }
        }
    }

    var atlasTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(atlasLayers, atlasLayers.combinedVersion) {
        atlasTick++
        animTick++
    }

    val drawText = animZoom > textCfg.visibilityZoomThreshold
    val drawEdges = animZoom > edgeCfg.visibilityZoomThreshold

    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.onSizeChanged { boxSize = it }) {
        Canvas(modifier = Modifier.matchParentSize()) {
            @Suppress("UNUSED_EXPRESSION") animTick
            @Suppress("UNUSED_EXPRESSION") coordinatesVersion
            @Suppress("UNUSED_EXPRESSION") atlasTick

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

            buffers.ensureCapacity(nodes.size * 2)
            var visibleNodeCount = 0

            val posArray = buffers.positions
            val texArray = buffers.texCoords
            val colArray = buffers.colors
            val idxArray = buffers.indices

            // OPTIMIZATION: Extract colors out of the loop
            val solidBackgroundColor = Color(0xFF121212)
            val fadedNodeAlpha = selectionCfg.fadedNodeAlpha
            val fadedEdgeAlpha = selectionCfg.fadedEdgeAlpha
            val whiteColorInt = Color.White.toArgb()

            for (i in nodes.indices) {
                val node = nodes[i]
                val pos = coordinates[node.id] ?: continue
                if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue

                // Retrieve cached data instead of calculating it
                val staticData = staticNodeData[i]
                val baseRadius = staticData.baseRadius
                val baseColor = staticData.baseColor
                val iconLookup = staticData.iconLookup
                val hasIcon = iconLookup != null

                val state = nodeAnimStates[node.id]
                val dim = state?.dimFactor ?: 0f
                val activeKoef = state?.activeKoef ?: 0f

                val r = baseRadius * lerp(1f, selectionCfg.scaleOnHover, activeKoef)
                val hoverOrDragColor =
                    if (node.id == draggedNodeId) theme.draggedNodeColor else theme.hoveredNodeColor

                val base = if (activeKoef > 0f) {
                    lerp(baseColor, hoverOrDragColor, activeKoef)
                } else {
                    baseColor
                }

                val nodeColorInt = if (dim > 0f) {
                    lerp(base, solidBackgroundColor, dim * (1f - fadedNodeAlpha)).toArgb()
                } else {
                    base.toArgb() // Avoids lerp entirely when completely visible
                }

                // ONLY draw the background circle if there is NO icon
                if (!hasIcon) {
                    val vOff = visibleNodeCount * 4
                    val fOff = vOff * 2
                    val iOff = visibleNodeCount * 6

                    posArray[fOff + 0] = pos.x - r; posArray[fOff + 1] = pos.y - r
                    posArray[fOff + 2] = pos.x + r; posArray[fOff + 3] = pos.y - r
                    posArray[fOff + 4] = pos.x + r; posArray[fOff + 5] = pos.y + r
                    posArray[fOff + 6] = pos.x - r; posArray[fOff + 7] = pos.y + r

                    texArray[fOff + 0] = -101f; texArray[fOff + 1] = -101f
                    texArray[fOff + 2] = -99f; texArray[fOff + 3] = -101f
                    texArray[fOff + 4] = -99f; texArray[fOff + 5] = -99f
                    texArray[fOff + 6] = -101f; texArray[fOff + 7] = -99f

                    colArray[vOff + 0] = nodeColorInt
                    colArray[vOff + 1] = nodeColorInt
                    colArray[vOff + 2] = nodeColorInt
                    colArray[vOff + 3] = nodeColorInt

                    idxArray[iOff + 0] = (vOff + 0).toShort(); idxArray[iOff + 1] =
                        (vOff + 1).toShort()
                    idxArray[iOff + 2] = (vOff + 2).toShort(); idxArray[iOff + 3] =
                        (vOff + 0).toShort()
                    idxArray[iOff + 4] = (vOff + 2).toShort(); idxArray[iOff + 5] =
                        (vOff + 3).toShort()

                    visibleNodeCount++
                }

                // Icon drawing logic
                if (hasIcon && iconLookup != null) {
                    val iconAlpha = lerp(1f, fadedNodeAlpha, dim)
                    // Avoid color allocation overhead if alpha is 1f
                    val iconFadedColorInt =
                        if (iconAlpha >= 0.99f) whiteColorInt else Color.White.copy(alpha = iconAlpha)
                            .toArgb()

                    val layer = atlasLayers.layers[iconLookup.layerIndex]
                    val atlasCols = layer.columns
                    val atlasTileSize = layer.tileSizePx.toFloat()
                    val inset = 0.5f

                    val rawU = (iconLookup.tileIndex % atlasCols) * atlasTileSize + inset
                    val rawV = (iconLookup.tileIndex / atlasCols) * atlasTileSize + inset
                    val texSpan = atlasTileSize - (inset * 2f)

                    val layerShift = iconLookup.layerIndex * GraphShader.STRIDE.toFloat()
                    val texU = rawU + layerShift
                    val texV = rawV

                    val vOff = visibleNodeCount * 4
                    val fOff = vOff * 2
                    val iOff = visibleNodeCount * 6

                    posArray[fOff + 0] = pos.x - r; posArray[fOff + 1] = pos.y - r
                    posArray[fOff + 2] = pos.x + r; posArray[fOff + 3] = pos.y - r
                    posArray[fOff + 4] = pos.x + r; posArray[fOff + 5] = pos.y + r
                    posArray[fOff + 6] = pos.x - r; posArray[fOff + 7] = pos.y + r

                    texArray[fOff + 0] = texU; texArray[fOff + 1] = texV
                    texArray[fOff + 2] = texU + texSpan; texArray[fOff + 3] = texV
                    texArray[fOff + 4] = texU + texSpan; texArray[fOff + 5] = texV + texSpan
                    texArray[fOff + 6] = texU; texArray[fOff + 7] = texV + texSpan

                    colArray[vOff + 0] = iconFadedColorInt
                    colArray[vOff + 1] = iconFadedColorInt
                    colArray[vOff + 2] = iconFadedColorInt
                    colArray[vOff + 3] = iconFadedColorInt

                    idxArray[iOff + 0] = (vOff + 0).toShort(); idxArray[iOff + 1] =
                        (vOff + 1).toShort()
                    idxArray[iOff + 2] = (vOff + 2).toShort(); idxArray[iOff + 3] =
                        (vOff + 0).toShort()
                    idxArray[iOff + 4] = (vOff + 2).toShort(); idxArray[iOff + 5] =
                        (vOff + 3).toShort()

                    visibleNodeCount++
                }
            }

            withTransform({
                scale(animZoom, animZoom)
                translate(center.x + movementOffset.x, center.y + movementOffset.y)
            }) {
                // -------- Group hulls (background layer) -----------------------------
                if (groupSettings.enabled && hulls.isNotEmpty()) {
                    val strokePx = (groupSettings.hullStrokeWidth / animZoom).coerceAtLeast(0.5f)
                    val stroke = Stroke(
                        width = strokePx,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    )

                    for (i in hulls.indices) {
                        val h = hulls[i]
                        // Fast bounds-cull: skip hulls fully outside the visible rect.
                        val bounds = h.path.getBounds()
                        if (bounds.right < cullL || bounds.left > cullR ||
                            bounds.bottom < cullT || bounds.top > cullB
                        ) continue

                        if (groupSettings.hullFill) {
                            drawPath(
                                path = h.path,
                                color = h.color.copy(alpha = h.color.alpha * groupSettings.hullFillAlpha),
                            )
                        }
                        drawPath(
                            path = h.path,
                            color = h.color,
                            style = stroke,
                        )
                    }
                }

                if (drawEdges) {
                    val strokeNormal = edgeCfg.strokeWidth / animZoom
                    val strokeHighlight =
                        (edgeCfg.strokeWidth + edgeCfg.strokeHighlightBonus) / animZoom

                    val baseEdgeColor = theme.resolvedEdgeColor
                    val hasSelectionFrame = activeNodeId != null

                    // OPTIMIZATION: Calculate this once outside the nested loop
                    val dimmedLine = lerp(
                        baseEdgeColor,
                        baseEdgeColor.copy(alpha = fadedEdgeAlpha),
                        if (hasSelectionFrame) 1f else 0f
                    )
                    val accentColor = theme.accentColor

                    for (i in nodes.indices) {
                        val sId = nodes[i].id
                        val sPos = coordinates[sId] ?: continue
                        val conns = connections[sId] ?: continue
                        val sActive = nodeAnimStates[sId]?.activeKoef ?: 0f

                        for (j in conns.indices) {
                            val tId = conns[j]

                            // ADD THIS CHECK: Ensure the target node actually exists
                            if (tId !in nodeById) continue

                            val tPos = coordinates[tId] ?: continue

                            val minX = min(sPos.x, tPos.x)
                            val maxX = max(sPos.x, tPos.x)
                            val minY = min(sPos.y, tPos.y)
                            val maxY = max(sPos.y, tPos.y)
                            if (maxX < cullL || minX > cullR || maxY < cullT || minY > cullB) continue

                            val tActive = nodeAnimStates[tId]?.activeKoef ?: 0f
                            val maxActive = max(sActive, tActive)

                            val stroke = strokeNormal + (strokeHighlight - strokeNormal) * maxActive

                            // OPTIMIZATION: Only lerp if the edge is actively highlighting
                            val edgeColor = if (maxActive > 0f) {
                                lerp(dimmedLine, accentColor, maxActive)
                            } else {
                                dimmedLine
                            }

                            drawLine(
                                color = edgeColor,
                                start = sPos,
                                end = tPos,
                                strokeWidth = stroke
                            )
                        }
                    }
                }

                if (watchNodeId != null) {
                    val watchPos = coordinates[watchNodeId]
                    if (watchPos != null) {
                        val watchNodeIndex = nodes.indexOfFirst { it.id == watchNodeId }
                        val watchRadius =
                            if (watchNodeIndex >= 0) staticNodeData[watchNodeIndex].baseRadius else circleRadius

                        drawCircle(
                            color = theme.accentColor,
                            radius = watchRadius * watchCfg.radiusMultiplier,
                            center = watchPos,
                            style = Stroke(width = watchCfg.strokeWidth / animZoom)
                        )
                    }
                }

                if (visibleNodeCount > 0) {
                    val vFloats = visibleNodeCount * 8
                    val cInts = visibleNodeCount * 4
                    val iShorts = visibleNodeCount * 6

                    val exactPos = buffers.getExactPositions(vFloats)
                    val exactTex = buffers.getExactTexCoords(vFloats)
                    val exactCol = buffers.getExactColors(cInts)
                    val exactIdx = buffers.getExactIndices(iShorts)

                    posArray.copyInto(exactPos, 0, 0, vFloats)
                    texArray.copyInto(exactTex, 0, 0, vFloats)
                    colArray.copyInto(exactCol, 0, 0, cInts)
                    idxArray.copyInto(exactIdx, 0, 0, iShorts)

                    drawContext.canvas.drawVertices2(
                        exactPos, exactCol, exactTex, exactIdx, shader = rtShader
                    )
                }

                drawCircle(color = theme.accentColor, radius = 1f / animZoom, center = Offset.Zero)
            }

            // -------- Hull labels (screen-space, drawn above nodes) ------------------
            val drawHullLabels = groupSettings.enabled &&
                    hulls.isNotEmpty() &&
                    animZoom > groupSettings.hullLabelVisibilityZoomThreshold

            if (drawHullLabels) {
                val hullTextScale = if (groupSettings.hullLabelScaleWithZoom) {
                    animZoom.coerceIn(
                        groupSettings.hullLabelMinScale,
                        groupSettings.hullLabelMaxScale
                    )
                } else {
                    1f
                }

                val hullZoomAlpha = ((animZoom - groupSettings.hullLabelVisibilityZoomThreshold) /
                        groupSettings.hullLabelVisibilityZoomFadeWidth.coerceAtLeast(0.0001f))
                    .coerceIn(0f, 1f)

                if (hullZoomAlpha >= 0.01f) {
                    for (i in hulls.indices) {
                        val h = hulls[i]
                        val layout = hullLabelLayouts[h.groupId] ?: continue

                        val sx = (h.labelAnchor.x + movementOffset.x) * animZoom + centerX
                        val sy = (h.labelAnchor.y + movementOffset.y) * animZoom + centerY -
                                groupSettings.hullLabelVerticalOffset

                        val scaledW = layout.size.width * hullTextScale
                        val scaledH = layout.size.height * hullTextScale
                        if (sx + scaledW < 0f || sx - scaledW > canvasW ||
                            sy + scaledH < 0f || sy - scaledH > canvasH
                        ) continue

                        val topLeft = Offset(
                            sx - layout.size.width / 2f,
                            sy - layout.size.height / 2f
                        )

                        if (hullTextScale != 1f) {
                            withTransform({
                                scale(
                                    scaleX = hullTextScale,
                                    scaleY = hullTextScale,
                                    pivot = Offset(sx, sy)
                                )
                            }) {
                                drawText(
                                    textLayoutResult = layout,
                                    topLeft = topLeft,
                                    color = h.color,
                                    alpha = hullZoomAlpha,
                                )
                            }
                        } else {
                            drawText(
                                textLayoutResult = layout,
                                topLeft = topLeft,
                                color = h.color,
                                alpha = hullZoomAlpha,
                            )
                        }
                    }
                }
            }

            if (drawText) {
                // Active node + its direct connections must always render text,
                // regardless of the maxTextsAtCenterVisible budget.
                val forceVisibleSet: Set<Id> = if (activeNodeId != null) {
                    val conns = connections[activeNodeId]
                    if (conns.isNullOrEmpty()) setOf(activeNodeId)
                    else HashSet<Id>(conns.size + 1).apply {
                        add(activeNodeId)
                        addAll(conns)
                    }
                } else emptySet()

                var visibleTextCount = 0

                for (i in nodes.indices) {
                    val node = nodes[i]
                    // Active node is rendered by the pill/popup path below — skip here.
                    if (activeNodeId == node.id) continue

                    val pos = coordinates[node.id] ?: continue
                    if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue

                    val alpha = nodeAnimStates[node.id]?.textAlpha ?: 1f
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
                    data.forced = node.id in forceVisibleSet
                }

                val activeVisibleTexts = visibleTextsPool.subList(0, visibleTextCount)
                // Forced labels first, then by distance to center.
                activeVisibleTexts.sortWith(
                    compareByDescending<VisibleTextData> { it.forced }.thenBy { it.distSq }
                )

                // Honor the forced set even if it exceeds maxTextsAtCenterVisible;
                // selection clarity beats the cap.
                val forcedCount = forceVisibleSet.count { it != activeNodeId && it in nodeById }
                val limit = min(visibleTextCount, max(maxTextsAtCenterVisible, forcedCount))

                val zoomFadeStart = textCfg.visibilityZoomThreshold
                val zoomFadeWidth = textCfg.visibilityZoomFadeWidth

                for (k in 0 until limit) {
                    val textData = activeVisibleTexts[k]
                    val nodeIndex = textData.nodeIndex
                    val screenPos = Offset(textData.screenX, textData.screenY)

                    val node = nodes[nodeIndex]
                    val layout = textLayouts[node.id] ?: continue

                    val nodeRadius = staticNodeData[nodeIndex].baseRadius
                    val nodeTextAlpha = nodeAnimStates[node.id]?.textAlpha ?: 1f
                    val zoomAlpha = ((animZoom - zoomFadeStart) / zoomFadeWidth).coerceIn(0f, 1f)
                    // Forced labels bypass the zoom-fade so they stay visible
                    // when the user is interacting with a selection.
                    val effectiveZoomAlpha = if (textData.forced) 1f else zoomAlpha
                    val finalAlpha = (nodeTextAlpha * effectiveZoomAlpha).coerceIn(0f, 1f)
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

            if (customPopup == null && activeNodeId != null && activeNodeTextLayout != null && cursorTextAlpha > 0.01f) {
                val activePos = coordinates[activeNodeId]
                if (activePos != null) {
                    val activeNodeIndex = nodes.indexOfFirst { it.id == activeNodeId }
                    val nodeRadius =
                        if (activeNodeIndex >= 0) staticNodeData[activeNodeIndex].baseRadius else circleRadius

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

                val activeNodeIndex = nodes.indexOfFirst { it.id == activeNodeId }
                val nodeRadius =
                    if (activeNodeIndex >= 0) staticNodeData[activeNodeIndex].baseRadius else circleRadius

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