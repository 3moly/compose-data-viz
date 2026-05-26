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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
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
import com.moly3.dataviz.core.graph.model.ArrowHead
import com.moly3.dataviz.core.graph.model.Connection
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.core.graph.model.LineStyle
import com.moly3.dataviz.core.graph.model.resolve
import com.moly3.dataviz.func.half
import com.moly3.dataviz.graph.features.atlas.AtlasLayers
import com.moly3.dataviz.graph.features.atlas.NodeStaticData
import com.moly3.dataviz.graph.func.approach
import com.moly3.shaders.buildEffect
import com.moly3.shaders.drawVertices2
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.max
import kotlin.math.min

// Pre-allocated object to track edges and their distance to center without garbage collection
private class VisibleEdgeData {
    var sIndex: Int = -1
    var tIndex: Int = -1
    var connIndex: Int = -1
    var distSq: Float = 0f
}

@Composable
fun <Id, Data> GraphInternal(
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
    settings: GraphSettings,
    atlasLayers: AtlasLayers = AtlasLayers.EMPTY,
    getIconKey: (Id, Data) -> String? = { _, _ -> null },
    nodes: List<GraphNode<Id, Data>>,

    connections: Map<Id, List<Connection<Id>>>,
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

    // Pools to avoid GC during panning/zooming
    val visibleTextsPool = remember { ArrayList<VisibleTextData>() }
    val visibleEdgesPool = remember { ArrayList<VisibleEdgeData>() }
    val edgeComparator =
        remember { Comparator<VisibleEdgeData> { a, b -> a.distSq.compareTo(b.distSq) } }

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

    val hullLabelSignature = remember(hulls) {
        var h = hulls.size
        for (i in hulls.indices) {
            val hull = hulls[i]
            h = h * 31 xor hull.groupId.hashCode()
            h = h * 31 xor hull.label.hashCode()
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
        if (activeNodeId != null) {
            connections[activeNodeId]?.mapTo(HashSet()) { it.target } ?: emptySet()
        } else emptySet()
    }

    val nodeAnimStates = remember { HashMap<Id, NodeAnimState>() }
    remember(nodes) {
        val currentIds = nodes.mapTo(HashSet()) { it.id }
        val it = nodeAnimStates.keys.iterator()
        while (it.hasNext()) if (it.next() !in currentIds) it.remove()
    }

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

    val nodeIndexById = remember(nodes) {
        HashMap<Id, Int>(nodes.size).apply {
            for (i in nodes.indices) put(nodes[i].id, i)
        }
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

            val solidBackgroundColor = Color(0xFF121212)
            val fadedNodeAlpha = selectionCfg.fadedNodeAlpha
            val fadedEdgeAlpha = selectionCfg.fadedEdgeAlpha
            val whiteColorInt = Color.White.toArgb()

            for (i in nodes.indices) {
                val node = nodes[i]
                val pos = coordinates[node.id] ?: continue
                if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue

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
                    base.toArgb()
                }

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

                if (hasIcon && iconLookup != null) {
                    val iconAlpha = lerp(1f, fadedNodeAlpha, dim)
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
                if (groupSettings.enabled && hulls.isNotEmpty()) {
                    val strokePx = (groupSettings.hullStrokeWidth / animZoom).coerceAtLeast(0.5f)
                    val stroke = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )

                    for (i in hulls.indices) {
                        val h = hulls[i]
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
                    val strokeScale = edgeCfg.strokeScalePolicy
                    val strokeNormal = strokeScale.resolve(edgeCfg.strokeWidth, animZoom)
                    val strokeHighlight = strokeScale.resolve(
                        edgeCfg.strokeWidth + edgeCfg.strokeHighlightBonus, animZoom
                    )

                    val baseEdgeColor = theme.resolvedEdgeColor
                    val accentColor = theme.accentColor

                    val dashScale = edgeCfg.dashPatternScalePolicy
                    val dashOn = dashScale.resolve(edgeCfg.dashOnPx, animZoom)
                    val dashOff = dashScale.resolve(edgeCfg.dashOffPx, animZoom)
                    val dotOn = dashScale.resolve(edgeCfg.dotOnPx, animZoom)
                    val dotOff = dashScale.resolve(edgeCfg.dotOffPx, animZoom)

                    val arrowScale = edgeCfg.arrowHeadScalePolicy
                    val headLenWorld = arrowScale.resolve(edgeCfg.arrowHeadLengthPx, animZoom)
                    val headWidWorld = arrowScale.resolve(edgeCfg.arrowHeadWidthPx, animZoom)

                    val arrowPath = buffers.arrowPath
                    val dimTargetAlpha = fadedEdgeAlpha

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
                    val dotEffect = PathEffect.dashPathEffect(floatArrayOf(dotOn, dotOff), 0f)

                    val defaultDimmedLine =
                        baseEdgeColor.copy(alpha = baseEdgeColor.alpha * dimTargetAlpha)
                    val defaultNormalLine = baseEdgeColor

                    val forcePureLines = edgeCfg.drawPureLines
                    val maxStyled = edgeCfg.maxStyledEdgesTotal

                    val worldCenterX = -movementOffset.x
                    val worldCenterY = -movementOffset.y

                    var visibleEdgeCount = 0

                    // 1. Gather visible edges. Min/Max normalization guarantees identical distance
                    //    results for bidirectional connections between the exact same two nodes.
                    for (i in nodes.indices) {
                        val sId = nodes[i].id
                        val sPos = coordinates[sId] ?: continue
                        val conns = connections[sId] ?: continue
                        if (conns.isEmpty()) continue

                        for (j in conns.indices) {
                            val conn = conns[j]
                            val tId = conn.target

                            val tIdx = nodeIndexById[tId] ?: continue
                            val tPos = coordinates[tId] ?: continue

                            val minX = if (sPos.x < tPos.x) sPos.x else tPos.x
                            val maxX = if (sPos.x > tPos.x) sPos.x else tPos.x
                            val minY = if (sPos.y < tPos.y) sPos.y else tPos.y
                            val maxY = if (sPos.y > tPos.y) sPos.y else tPos.y

                            if (maxX < cullL || minX > cullR || maxY < cullT || minY > cullB) continue

                            val midX = (minX + maxX) * 0.5f
                            val midY = (minY + maxY) * 0.5f
                            val dx = midX - worldCenterX
                            val dy = midY - worldCenterY
                            val distSq = dx * dx + dy * dy

                            if (visibleEdgeCount >= visibleEdgesPool.size) {
                                visibleEdgesPool.add(VisibleEdgeData())
                            }

                            val data = visibleEdgesPool[visibleEdgeCount++]
                            data.sIndex = i
                            data.tIndex = tIdx
                            data.connIndex = j
                            data.distSq = distSq
                        }
                    }

                    // 2. Sort by distance to camera center
                    val activeEdges = visibleEdgesPool.subList(0, visibleEdgeCount)
                    activeEdges.sortWith(edgeComparator)

                    var drawnStyledCount = 0

                    // 3. Draw + Deduplicate!
                    for (k in 0 until visibleEdgeCount) {
                        val data = activeEdges[k]
                        val sIdx = data.sIndex
                        val tIdx = data.tIndex
                        val sId = nodes[sIdx].id
                        val tId = nodes[tIdx].id
                        val conn = connections[sId]!![data.connIndex]

                        val willBeStyled = !forcePureLines && drawnStyledCount < maxStyled
                        var isDuplicate = false

                        // DEDUPLICATION: Look backwards. Because of our deterministic midX/midY,
                        // duplicates will be clustered with the EXACT same distance.
                        for (prevK in k - 1 downTo 0) {
                            val prevData = activeEdges[prevK]
                            if (prevData.distSq != data.distSq) break

                            val prevSIdx = prevData.sIndex
                            val prevTIdx = prevData.tIndex

                            val sameDirect = sIdx == prevSIdx && tIdx == prevTIdx
                            val sameReverse = sIdx == prevTIdx && tIdx == prevSIdx

                            if (sameDirect || sameReverse) {
                                // If we ran out of style budget, we just need ANY connecting line.
                                // If a line already exists here, we don't need a redundant pure line.
                                if (!willBeStyled) {
                                    isDuplicate = true
                                    break
                                }

                                val prevConn = connections[nodes[prevSIdx].id]!![prevData.connIndex]

                                // Check if styles are equivalent
                                if (conn.style == prevConn.style) {
                                    // If it has an arrow pointing the opposite way, we must keep both
                                    if (conn.style.head != ArrowHead.None && sameReverse) {
                                        // Keep searching backwards just in case
                                    } else {
                                        isDuplicate = true
                                        break
                                    }
                                }
                            }
                        }

                        if (isDuplicate) continue

                        if (willBeStyled) drawnStyledCount++

                        val sState = nodeAnimStates[sId]
                        val tState = nodeAnimStates[tId]
                        val sActive = sState?.activeKoef ?: 0f
                        val tActive = tState?.activeKoef ?: 0f
                        val sDim = sState?.dimFactor ?: 0f
                        val tDim = tState?.dimFactor ?: 0f
                        val sRadius = staticNodeData[sIdx].baseRadius

                        val maxActive = if (sActive > tActive) sActive else tActive
                        val edgeDim = if (sDim < tDim) sDim else tDim

                        val stroke = if (maxActive == 0f) strokeNormal
                        else strokeNormal + (strokeHighlight - strokeNormal) * maxActive

                        val effectiveLineStyle =
                            if (willBeStyled) conn.style.line else LineStyle.Solid
                        val effectiveHead = if (willBeStyled) conn.style.head else ArrowHead.None
                        val isCustomColor =
                            if (willBeStyled) conn.style.color.isSpecified else false

                        val edgeColor = if (!isCustomColor && maxActive == 0f) {
                            if (edgeDim >= 1f) defaultDimmedLine
                            else if (edgeDim <= 0f) defaultNormalLine
                            else {
                                val alphaScale = 1f - edgeDim * (1f - dimTargetAlpha)
                                baseEdgeColor.copy(alpha = baseEdgeColor.alpha * alphaScale)
                            }
                        } else {
                            val themedBase = if (isCustomColor) conn.style.color else baseEdgeColor
                            val highlighted = if (maxActive > 0f) lerp(
                                themedBase,
                                accentColor,
                                maxActive
                            ) else themedBase

                            if (edgeDim > 0f) {
                                val alphaScale = 1f - edgeDim * (1f - dimTargetAlpha)
                                highlighted.copy(alpha = highlighted.alpha * alphaScale)
                            } else highlighted
                        }

                        val hasArrow = effectiveHead != ArrowHead.None
                        val needsOffset = hasArrow || effectiveLineStyle != LineStyle.Solid

                        val drawStartX: Float
                        val drawStartY: Float
                        val drawEndX: Float
                        val drawEndY: Float
                        val ux: Float
                        val uy: Float

                        val sPos = coordinates[sId] ?: continue
                        val tPos = coordinates[tId] ?: continue

                        if (!needsOffset) {
                            drawStartX = sPos.x
                            drawStartY = sPos.y
                            drawEndX = tPos.x
                            drawEndY = tPos.y
                            ux = 0f
                            uy = 0f
                        } else {
                            val tRadius = staticNodeData[tIdx].baseRadius
                            val dx = tPos.x - sPos.x
                            val dy = tPos.y - sPos.y
                            val lenSq = dx * dx + dy * dy

                            if (lenSq < 0.0001f) continue
                            val invLen = 1f / kotlin.math.sqrt(lenSq)
                            ux = dx * invLen
                            uy = dy * invLen

                            drawStartX = sPos.x + ux * sRadius
                            drawStartY = sPos.y + uy * sRadius
                            drawEndX = tPos.x - ux * tRadius
                            drawEndY = tPos.y - uy * tRadius

                            val tdx = drawEndX - drawStartX
                            val tdy = drawEndY - drawStartY
                            if (tdx * ux + tdy * uy <= 0f) continue
                        }

                        val drawStart = Offset(drawStartX, drawStartY)
                        val drawEnd = Offset(drawEndX, drawEndY)

                        when (effectiveLineStyle) {
                            LineStyle.Solid -> drawLine(edgeColor, drawStart, drawEnd, stroke)
                            LineStyle.Dashed -> drawLine(
                                edgeColor,
                                drawStart,
                                drawEnd,
                                stroke,
                                pathEffect = dashEffect
                            )

                            LineStyle.Dotted -> drawLine(
                                edgeColor,
                                drawStart,
                                drawEnd,
                                stroke,
                                cap = StrokeCap.Round,
                                pathEffect = dotEffect
                            )
                        }

                        if (hasArrow) {
                            drawArrowHead(
                                path = arrowPath,
                                tip = drawEnd,
                                dirX = ux, dirY = uy,
                                head = effectiveHead,
                                length = headLenWorld,
                                width = headWidWorld,
                                color = edgeColor,
                                strokeWidth = stroke,
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
                val forceVisibleSet: Set<Id> = if (activeNodeId != null) {
                    val conns = connections[activeNodeId]
                    if (conns.isNullOrEmpty()) setOf(activeNodeId)
                    else HashSet<Id>(conns.size + 1).apply {
                        add(activeNodeId)
                        for (c in conns) add(c.target)
                    }
                } else emptySet()

                var visibleTextCount = 0

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
                activeVisibleTexts.sortWith(
                    compareByDescending<VisibleTextData> { it.forced }.thenBy { it.distSq }
                )

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

private inline fun DrawScope.drawArrowHead(
    path: Path,
    tip: Offset,
    dirX: Float, dirY: Float,
    head: ArrowHead,
    length: Float,
    width: Float,
    color: Color,
    strokeWidth: Float,
) {
    if (head == ArrowHead.None) return

    val px = -dirY
    val py = dirX

    val bx = tip.x - dirX * length
    val by = tip.y - dirY * length

    val half = width * 0.5f
    val leftX = bx + px * half
    val leftY = by + py * half
    val rightX = bx - px * half
    val rightY = by - py * half

    when (head) {
        ArrowHead.None -> Unit

        ArrowHead.Open -> {
            drawLine(color, Offset(leftX, leftY), tip, strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(rightX, rightY), tip, strokeWidth, cap = StrokeCap.Round)
        }

        ArrowHead.FilledTriangle -> {
            path.rewind()
            path.moveTo(tip.x, tip.y)
            path.lineTo(leftX, leftY)
            path.lineTo(rightX, rightY)
            path.close()
            drawPath(path, color)
        }

        ArrowHead.HollowTriangle -> {
            path.rewind()
            path.moveTo(tip.x, tip.y)
            path.lineTo(leftX, leftY)
            path.lineTo(rightX, rightY)
            path.close()
            drawPath(path, color, style = Stroke(width = strokeWidth, join = StrokeJoin.Miter))
        }

        ArrowHead.FilledDiamond, ArrowHead.HollowDiamond -> {
            val backX = tip.x - dirX * (length * 2f)
            val backY = tip.y - dirY * (length * 2f)
            path.rewind()
            path.moveTo(tip.x, tip.y)
            path.lineTo(leftX, leftY)
            path.lineTo(backX, backY)
            path.lineTo(rightX, rightY)
            path.close()
            if (head == ArrowHead.FilledDiamond) {
                drawPath(path, color)
            } else {
                drawPath(path, color, style = Stroke(width = strokeWidth, join = StrokeJoin.Miter))
            }
        }
    }
}