// Changed Code
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
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.core.graph.model.GraphTheme
import com.moly3.dataviz.func.half
import com.moly3.dataviz.graph.func.getNodeConnections
import com.moly3.shaders.RuntimeEffect
import com.moly3.shaders.buildEffect
import com.moly3.shaders.drawVertices2
import kotlin.math.max
import kotlin.math.min

private class GraphBuffers {
    var positions = FloatArray(0)
    var texCoords = FloatArray(0)
    var colors = IntArray(0)
    var indices = ShortArray(0)

    private var exactPositions = FloatArray(0)
    private var exactTexCoords = FloatArray(0)
    private var exactColors = IntArray(0)
    private var exactIndices = ShortArray(0)

    fun ensureCapacity(nodeCount: Int) {
        val reqVerts = nodeCount * 4
        val reqIndices = nodeCount * 6
        if (positions.size < reqVerts * 2) positions = FloatArray(reqVerts * 4)
        if (texCoords.size < reqVerts * 2) texCoords = FloatArray(reqVerts * 4)
        if (colors.size < reqVerts) colors = IntArray(reqVerts * 2)
        if (indices.size < reqIndices) indices = ShortArray(reqIndices * 2)
    }

    fun getExactPositions(size: Int): FloatArray {
        if (exactPositions.size != size) exactPositions = FloatArray(size)
        return exactPositions
    }
    fun getExactTexCoords(size: Int): FloatArray {
        if (exactTexCoords.size != size) exactTexCoords = FloatArray(size)
        return exactTexCoords
    }
    fun getExactColors(size: Int): IntArray {
        if (exactColors.size != size) exactColors = IntArray(size)
        return exactColors
    }
    fun getExactIndices(size: Int): ShortArray {
        if (exactIndices.size != size) exactIndices = ShortArray(size)
        return exactIndices
    }
}

private class NodeAnimState {
    var textAlpha: Float = 1f
    var dimFactor: Float = 0f
    var activeKoef: Float = 0f
}

private fun approach(current: Float, target: Float, rate: Float, dtSec: Float): Float {
    if (current == target) return target
    val step = rate * dtSec
    return if (current < target) min(current + step, target)
    else max(current - step, target)
}

@Composable
internal fun <Id, Data> GraphInternal(
    modifier: Modifier = Modifier,
    settings: GraphSettings,
    atlas: TextureAtlas? = null,
    getIconIndex: (Id) -> Int? = { null },
    nodes: List<GraphNode<Id, Data>>,
    connections: Map<Id, List<Id>>,
    coordinates: Map<Id, Offset>,
    draggedNodeId: Id?,
    cursorNodeId: Id?,
    watchNodeId: Id?,
    movementOffset: Offset,
    zoom: Float,
    customPopup: (@Composable (node: GraphNode<Id, Data>) -> Unit)? = null
) {
    val theme        = settings.theme
    val view         = settings.view
    val selectionCfg = settings.selection
    val edgeCfg      = settings.edge
    val textCfg      = settings.text
    val watchCfg     = settings.watch
    val baseTextStyle = settings.textStyle

    val circleRadius          = view.circleSize
    val circleSizeMultiplier  = view.circleSizeMultiplier
    val maxTextsAtCenterVisible = textCfg.maxLabelsVisible
    val fallbackBitmap = remember(atlas) {
        androidx.compose.ui.graphics.ImageBitmap(1, 1)
    }

    val shader        = remember { GraphShader }
    val runtimeEffect = remember(shader) { buildEffect(shader) }
    val buildShader = remember(runtimeEffect, atlas, view, fallbackBitmap) {
        runtimeEffect.apply {
            setFloatUniform("uQuality", view.circleQuality)
            setFloatUniform("uBorderWidth", view.circleBorderWidth)

            if (atlas != null) {
                setFloatUniform("uUseAtlas", 1f)
                setImageUniform("uAtlas", atlas.imageBitmap)
                setFloatUniform("uTileSize", atlas.tileSizePx.toFloat())
                setFloatUniform("uColumns", atlas.columns.toFloat())
            } else {
                setFloatUniform("uUseAtlas", 0f)
                setImageUniform("uAtlas", fallbackBitmap)
                setFloatUniform("uTileSize", 1f)
                setFloatUniform("uColumns", 1f)
            }
        }.buildShader()
    }
    val animZoom = zoom

    val localDensity = LocalDensity.current
    val textPadding = remember(textCfg.labelPaddingDp) {
        localDensity.run { textCfg.labelPaddingDp.toDp().toPx() }
    }
    val textMeasurer          = rememberTextMeasurer(cacheSize = Int.MAX_VALUE)
    val textMeasurerNoCaching = rememberTextMeasurer(cacheSize = 0)

    val buffers  = remember { GraphBuffers() }
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

    val nodeAnimStates = remember { HashMap<Id, NodeAnimState>() }
    remember(nodes) {
        val currentIds = nodes.mapTo(HashSet()) { it.id }
        val it = nodeAnimStates.keys.iterator()
        while (it.hasNext()) if (it.next() !in currentIds) it.remove()
    }

    var cursorTextAlpha by remember { mutableStateOf(0f) }
    var animTick by remember { mutableStateOf(0) }

    val latestActiveNodeId       by rememberUpdatedState(activeNodeId)
    val latestActiveConnectionSet by rememberUpdatedState(activeConnectionSet)
    val latestNodes              by rememberUpdatedState(nodes)
    val latestSelectionCfg       by rememberUpdatedState(selectionCfg)

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
                        !hasSelection -> { targetText = 1f; targetDim = 0f }
                        node.id == currentActiveId -> { targetText = 0f; targetDim = 0f }
                        node.id in currentConnections -> { targetText = cfg.selectedTextAlpha; targetDim = 0f }
                        else -> { targetText = cfg.unrelatedTextAlpha; targetDim = 1f }
                    }

                    val rateText = if (targetText > state.textAlpha)
                        cfg.fadeInRatePerSec else cfg.fadeOutRatePerSec

                    val newText = approach(state.textAlpha, targetText, rateText, dtSec)
                    val newDim  = approach(state.dimFactor, targetDim, cfg.nodeDimRatePerSec, dtSec)
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

    val drawText  = animZoom > textCfg.visibilityZoomThreshold
    val drawEdges = animZoom > edgeCfg.visibilityZoomThreshold

    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.onSizeChanged { boxSize = it }) {
        Canvas(modifier = Modifier.matchParentSize()) {
            @Suppress("UNUSED_EXPRESSION") animTick

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

            val solidBackgroundColor = Color(0xFF121212)

            for (i in nodes.indices) {
                val node = nodes[i]
                val pos = coordinates[node.id] ?: continue
                if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue

                val baseRadius = GraphNode.getCircleSize(
                    circleRadius = circleRadius,
                    connectionCount = connections[node.id]?.size ?: 1,
                    multiplier = circleSizeMultiplier
                )

                val state = nodeAnimStates[node.id]
                val dim = state?.dimFactor ?: 0f
                val activeKoef = state?.activeKoef ?: 0f

                val r = baseRadius * lerp(1f, selectionCfg.scaleOnHover, activeKoef)

                val iconIndex = getIconIndex(node.id) ?: -1
                val hasIcon = iconIndex >= 0 && atlas != null

                val baseColor = node.colorValue?.let { Color(it) } ?: theme.nodeColor
                val hoverOrDragColor = if (node.id == draggedNodeId) theme.draggedNodeColor else theme.hoveredNodeColor
                val base = lerp(baseColor, hoverOrDragColor, activeKoef)

                val nodeColor = lerp(base, solidBackgroundColor, dim * (1f - selectionCfg.fadedNodeAlpha))
                val nodeColorInt = nodeColor.toArgb()

                val iconAlpha = lerp(1f, selectionCfg.fadedNodeAlpha, dim)
                val iconFadedColorInt = Color.White.copy(alpha = iconAlpha).toArgb()

                // ONLY draw the background circle if there is NO icon
                if (!hasIcon) {
                    var vOff = visibleNodeCount * 4
                    var fOff = vOff * 2
                    var iOff = visibleNodeCount * 6

                    posArray[fOff + 0] = pos.x - r; posArray[fOff + 1] = pos.y - r
                    posArray[fOff + 2] = pos.x + r; posArray[fOff + 3] = pos.y - r
                    posArray[fOff + 4] = pos.x + r; posArray[fOff + 5] = pos.y + r
                    posArray[fOff + 6] = pos.x - r; posArray[fOff + 7] = pos.y + r

                    texArray[fOff + 0] = -101f; texArray[fOff + 1] = -101f
                    texArray[fOff + 2] = -99f;  texArray[fOff + 3] = -101f
                    texArray[fOff + 4] = -99f;  texArray[fOff + 5] = -99f
                    texArray[fOff + 6] = -101f; texArray[fOff + 7] = -99f

                    colArray[vOff + 0] = nodeColorInt
                    colArray[vOff + 1] = nodeColorInt
                    colArray[vOff + 2] = nodeColorInt
                    colArray[vOff + 3] = nodeColorInt

                    idxArray[iOff + 0] = (vOff + 0).toShort(); idxArray[iOff + 1] = (vOff + 1).toShort()
                    idxArray[iOff + 2] = (vOff + 2).toShort(); idxArray[iOff + 3] = (vOff + 0).toShort()
                    idxArray[iOff + 4] = (vOff + 2).toShort(); idxArray[iOff + 5] = (vOff + 3).toShort()

                    visibleNodeCount++
                }

                // Draw the icon as usual (the shader will handle the square rendering without clipping)
                if (hasIcon) {
                    val atlasCols = atlas.columns.toFloat()
                    val atlasTileSize = atlas.tileSizePx.toFloat()

                    val inset = 0.5f

                    val texU = (iconIndex % atlasCols.toInt()) * atlasTileSize + inset
                    val texV = (iconIndex / atlasCols.toInt()) * atlasTileSize + inset
                    val texSpan = atlasTileSize - (inset * 2f)
//                    val texSpan = atlasTileSize

                    val vOff = visibleNodeCount * 4
                    val fOff = vOff * 2
                    val iOff = visibleNodeCount * 6

                    posArray[fOff + 0] = pos.x - r; posArray[fOff + 1] = pos.y - r
                    posArray[fOff + 2] = pos.x + r; posArray[fOff + 3] = pos.y - r
                    posArray[fOff + 4] = pos.x + r; posArray[fOff + 5] = pos.y + r
                    posArray[fOff + 6] = pos.x - r; posArray[fOff + 7] = pos.y + r

                    texArray[fOff + 0] = texU;           texArray[fOff + 1] = texV
                    texArray[fOff + 2] = texU + texSpan; texArray[fOff + 3] = texV
                    texArray[fOff + 4] = texU + texSpan; texArray[fOff + 5] = texV + texSpan
                    texArray[fOff + 6] = texU;           texArray[fOff + 7] = texV + texSpan

                    colArray[vOff + 0] = iconFadedColorInt
                    colArray[vOff + 1] = iconFadedColorInt
                    colArray[vOff + 2] = iconFadedColorInt
                    colArray[vOff + 3] = iconFadedColorInt

                    idxArray[iOff + 0] = (vOff + 0).toShort(); idxArray[iOff + 1] = (vOff + 1).toShort()
                    idxArray[iOff + 2] = (vOff + 2).toShort(); idxArray[iOff + 3] = (vOff + 0).toShort()
                    idxArray[iOff + 4] = (vOff + 2).toShort(); idxArray[iOff + 5] = (vOff + 3).toShort()

                    visibleNodeCount++
                }
            }

            withTransform({
                scale(animZoom, animZoom)
                translate(center.x + movementOffset.x, center.y + movementOffset.y)
            }) {
                if (drawEdges) {
                    val strokeNormal    = edgeCfg.strokeWidth / animZoom
                    val strokeHighlight = (edgeCfg.strokeWidth + edgeCfg.strokeHighlightBonus) / animZoom

                    val baseEdgeColor = theme.resolvedEdgeColor

                    for (i in nodes.indices) {
                        val sId = nodes[i].id
                        val sPos = coordinates[sId] ?: continue
                        val conns = connections[sId] ?: continue

                        for (j in conns.indices) {
                            val tId = conns[j]
                            val tPos = coordinates[tId] ?: continue

                            val minX = min(sPos.x, tPos.x); val maxX = max(sPos.x, tPos.x)
                            val minY = min(sPos.y, tPos.y); val maxY = max(sPos.y, tPos.y)
                            if (maxX < cullL || minX > cullR || maxY < cullT || minY > cullB) continue

                            val sActive = nodeAnimStates[sId]?.activeKoef ?: 0f
                            val tActive = nodeAnimStates[tId]?.activeKoef ?: 0f
                            val maxActive = max(sActive, tActive)

                            val stroke = strokeNormal + (strokeHighlight - strokeNormal) * maxActive

                            val dimmedLine = lerp(
                                baseEdgeColor,
                                baseEdgeColor.copy(alpha = selectionCfg.fadedEdgeAlpha),
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

                if (visibleNodeCount > 0) {
                    val vFloats = visibleNodeCount * 8
                    val cInts   = visibleNodeCount * 4
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
                        exactPos, exactCol, exactTex, exactIdx, shader = buildShader
                    )
                }

                drawCircle(color = theme.accentColor, radius = 1f / animZoom, center = Offset.Zero)
            }

            if (drawText) {
                val visibleTexts = ArrayList<Triple<Int, Float, Offset>>()

                for (i in nodes.indices) {
                    val node = nodes[i]
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

                    visibleTexts.add(Triple(i, distSq, Offset(screenX, screenY)))
                }

                visibleTexts.sortBy { it.second }

                val limit = min(visibleTexts.size, maxTextsAtCenterVisible)
                val zoomFadeStart = textCfg.visibilityZoomThreshold
                val zoomFadeWidth = textCfg.visibilityZoomFadeWidth

                for (k in 0 until limit) {
                    val (nodeIndex, _, screenPos) = visibleTexts[k]
                    val node = nodes[nodeIndex]
                    val layout = textLayouts[node.id] ?: continue

                    val nodeRadius = GraphNode.getCircleSize(
                        circleRadius, connections[node.id]?.size ?: 1, circleSizeMultiplier
                    )
                    val nodeTextAlpha = nodeAnimStates[node.id]?.textAlpha ?: 1f
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
                            width  = activeNodeTextLayout.size.width  + bgPadX * 2f,
                            height = activeNodeTextLayout.size.height + bgPadY * 2f
                        ),
                        cornerRadius = CornerRadius(textCfg.activePillCornerRadius, textCfg.activePillCornerRadius)
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

                Box(modifier = Modifier
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