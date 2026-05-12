package com.moly3.dataviz.graph.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.func.half
import com.moly3.dataviz.graph.func.getNodeConnections
import com.moly3.shaders.RuntimeEffect
import com.moly3.shaders.buildEffect
import com.moly3.shaders.drawVertices2
import kotlin.math.max
import kotlin.math.min

/**
 * Reusable buffer cache. Scratch arrays grow-only; exact arrays are reallocated
 * only when visible count changes (rare during pan/zoom).
 */
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

/**
 * Animation parameters tuned to match Obsidian's graph view feel.
 */
private object GraphAnim {
    const val FADE_IN_PER_SEC = 6.0f       // ~167ms full fade-in
    const val FADE_OUT_PER_SEC = 5.0f      // ~200ms full fade-out
    const val NODE_FADE_PER_SEC = 7.0f     // node color/dim transition
    const val EDGE_FADE_PER_SEC = 6.0f     // edge highlight transition
    const val FADED_NODE_ALPHA = 0.15f     // How visible unrelated nodes are
    const val FADED_EDGE_ALPHA = 0.05f     // How visible unrelated edges are
    const val SELECTED_TEXT_BOOST = 1.0f   // selected/connected stay full
    const val UNRELATED_TEXT_ALPHA = 0.0f  // unrelated nodes' text hides completely
}

/**
 * Per-node animated state used to drive smooth transitions on selection/drag.
 * `textAlpha`   – current text opacity (animated toward target)
 * `dimFactor`   – current dim factor for the node body (animated toward target)
 */
private class NodeAnimState {
    var textAlpha: Float = 1f
    var dimFactor: Float = 0f // 0 = full color, 1 = darkest
}

/**
 * Approach `current` toward `target` at `rate` per second.
 * Frame-rate independent, monotonic, no overshoot.
 */
private fun approach(current: Float, target: Float, rate: Float, dtSec: Float): Float {
    if (current == target) return target
    val step = rate * dtSec
    return if (current < target) {
        min(current + step, target)
    } else {
        max(current - step, target)
    }
}

private fun <Id, Data> getNodeBaseColor(
    node: GraphNode<Id, Data>,
    draggedNodeId: Id?,
    cursorNodeId: Id?,
    defaultColor: Color
): Color {
    if (node.id == draggedNodeId) return Color.Green
    if (node.id == cursorNodeId) return Color.Green
    return node.colorValue?.let { Color(it) } ?: defaultColor
}

@Composable
internal fun <Id, Data> GraphInternal(
    modifier: Modifier = Modifier,

    nodes: List<GraphNode<Id, Data>>,
    connections: Map<Id, List<Id>>,
    coordinates: Map<Id, Offset>,
    draggedNodeId: Id?,
    cursorNodeId: Id?,
    watchNodeId: Id?,

    movementOffset: Offset,
    circleRadius: Float,
    zoom: Float,

    circleSizeMultiplier: Float?,
    primaryColor: Color,
    textStyle: TextStyle = TextStyle.Default,
    fontColor: Color,
    circleColor: Color,
    circleLineColor: Color,
    maxTextsAtCenterVisible: Int = Int.MAX_VALUE,
) {
    val shader = remember { GraphShader }
    val runtimeEffect: RuntimeEffect = remember(shader) { buildEffect(shader) }
    val buildShader = remember(runtimeEffect) { runtimeEffect.buildShader() }

    val animZoom = zoom

    val cursorCircleSizeKoef by animateFloatAsState(
        targetValue = if (cursorNodeId != null || draggedNodeId != null) 1.5f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "activeScale"
    )

    val selectionActiveAmount by animateFloatAsState(
        targetValue = if (cursorNodeId != null || draggedNodeId != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "selectionActive"
    )

    val localDensity = LocalDensity.current
    val textPadding = remember { localDensity.run { 16.toDp().toPx() } }
    val textMeasurer = rememberTextMeasurer(cacheSize = Int.MAX_VALUE)
    val textMeasurerNoCaching = rememberTextMeasurer(cacheSize = 0)

    val buffers = remember { GraphBuffers() }
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

    val textLayouts = remember(textSignature, textStyle) {
        val style = textStyle.copy(fontSize = 12.sp)
        nodes.associate { node ->
            node.id to textMeasurer.measure(text = node.name, style = style)
        }
    }

    val activeNodeId: Id? = draggedNodeId ?: cursorNodeId

    // Added 'nodes' to remember keys to prevent stale node names
    val activeNodeTextLayout = remember(activeNodeId, nodes, textStyle, localDensity.density) {
        val style = textStyle.copy(fontSize = (24 / localDensity.density).sp)
        activeNodeId?.let { id ->
            nodeById[id]?.let { textMeasurerNoCaching.measure(text = it.name, style = style) }
        }
    }

    val activeConnectionSet = remember(activeNodeId, connections) {
        if (activeNodeId != null) {
            connections.getNodeConnections(activeNodeId).toHashSet()
        } else {
            emptySet()
        }
    }

    val nodeAnimStates = remember { HashMap<Id, NodeAnimState>() }
    remember(nodes) {
        val currentIds = nodes.mapTo(HashSet()) { it.id }
        val it = nodeAnimStates.keys.iterator()
        while (it.hasNext()) if (it.next() !in currentIds) it.remove()
    }

    var cursorTextAlpha by remember { mutableStateOf(0f) }
    var animTick by remember { mutableStateOf(0) }

    // CRITICAL FIX: Use rememberUpdatedState to avoid stale closures in the loop
    val latestActiveNodeId by rememberUpdatedState(activeNodeId)
    val latestActiveConnectionSet by rememberUpdatedState(activeConnectionSet)
    val latestNodes by rememberUpdatedState(nodes)

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val dtSec = if (lastNanos == 0L) {
                    1f / 60f
                } else {
                    ((nowNanos - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
                }
                lastNanos = nowNanos

                // Access the *latest* states inside the frame tick
                val currentActiveId = latestActiveNodeId
                val currentNodes = latestNodes
                val currentConnections = latestActiveConnectionSet

                val hasSelection = currentActiveId != null
                var anyChange = false

                for (i in currentNodes.indices) {
                    val node = currentNodes[i]
                    val state = nodeAnimStates.getOrPut(node.id) { NodeAnimState() }

                    val targetText: Float
                    val targetDim: Float
                    when {
                        !hasSelection -> {
                            targetText = 1f
                            targetDim = 0f
                        }
                        node.id == currentActiveId -> {
                            targetText = 0f
                            targetDim = 0f
                        }
                        node.id in currentConnections -> {
                            targetText = GraphAnim.SELECTED_TEXT_BOOST
                            targetDim = 0f
                        }
                        else -> {
                            targetText = GraphAnim.UNRELATED_TEXT_ALPHA
                            targetDim = 1f
                        }
                    }

                    val rateText = if (targetText > state.textAlpha)
                        GraphAnim.FADE_IN_PER_SEC else GraphAnim.FADE_OUT_PER_SEC

                    val newText = approach(state.textAlpha, targetText, rateText, dtSec)
                    val newDim = approach(state.dimFactor, targetDim, GraphAnim.NODE_FADE_PER_SEC, dtSec)

                    if (newText != state.textAlpha || newDim != state.dimFactor) {
                        state.textAlpha = newText
                        state.dimFactor = newDim
                        anyChange = true
                    }
                }

                val cursorTarget = if (hasSelection) 1f else 0f
                val cursorRate = if (cursorTarget > cursorTextAlpha) GraphAnim.FADE_IN_PER_SEC else GraphAnim.FADE_OUT_PER_SEC
                val newCursor = approach(cursorTextAlpha, cursorTarget, cursorRate, dtSec)
                if (newCursor != cursorTextAlpha) {
                    cursorTextAlpha = newCursor
                    anyChange = true
                }

                if (anyChange) animTick++
            }
        }
    }

    @Suppress("UNUSED_EXPRESSION") animTick

    val drawText = animZoom > 0.5f
    val drawEdges = animZoom > 0.15f

    Canvas(modifier = modifier) {
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

        buffers.ensureCapacity(nodes.size)
        var visibleNodeCount = 0

        val posArray = buffers.positions
        val texArray = buffers.texCoords
        val colArray = buffers.colors
        val idxArray = buffers.indices

        for (i in nodes.indices) {
            val node = nodes[i]
            val pos = coordinates[node.id] ?: continue
            if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue

            val baseRadius = GraphNode.getCircleSize(
                circleRadius = circleRadius,
                connectionCount = connections[node.id]?.size ?: 1,
                multiplier = circleSizeMultiplier
            )
            val r = if (node.id == activeNodeId) baseRadius * cursorCircleSizeKoef else baseRadius

            val base = getNodeBaseColor(node, draggedNodeId, cursorNodeId, circleColor)
            val dim = nodeAnimStates[node.id]?.dimFactor ?: 0f

            val color = lerp(base, base.copy(alpha = GraphAnim.FADED_NODE_ALPHA), dim)
            val colorInt = color.toArgb()

            val vOff = visibleNodeCount * 4
            val fOff = vOff * 2

            posArray[fOff + 0] = pos.x - r; posArray[fOff + 1] = pos.y - r
            posArray[fOff + 2] = pos.x + r; posArray[fOff + 3] = pos.y - r
            posArray[fOff + 4] = pos.x + r; posArray[fOff + 5] = pos.y + r
            posArray[fOff + 6] = pos.x - r; posArray[fOff + 7] = pos.y + r

            texArray[fOff + 0] = -1f; texArray[fOff + 1] = -1f
            texArray[fOff + 2] = 1f; texArray[fOff + 3] = -1f
            texArray[fOff + 4] = 1f; texArray[fOff + 5] = 1f
            texArray[fOff + 6] = -1f; texArray[fOff + 7] = 1f

            colArray[vOff + 0] = colorInt
            colArray[vOff + 1] = colorInt
            colArray[vOff + 2] = colorInt
            colArray[vOff + 3] = colorInt

            val iOff = visibleNodeCount * 6
            idxArray[iOff + 0] = (vOff + 0).toShort(); idxArray[iOff + 1] = (vOff + 1).toShort()
            idxArray[iOff + 2] = (vOff + 2).toShort(); idxArray[iOff + 3] = (vOff + 0).toShort()
            idxArray[iOff + 4] = (vOff + 2).toShort(); idxArray[iOff + 5] = (vOff + 3).toShort()

            visibleNodeCount++
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
            for (k in 0 until limit) {
                val (nodeIndex, _, screenPos) = visibleTexts[k]
                val node = nodes[nodeIndex]
                val layout = textLayouts[node.id] ?: continue

                val nodeRadius = GraphNode.getCircleSize(circleRadius, connections[node.id]?.size ?: 1, circleSizeMultiplier)
                val nodeTextAlpha = nodeAnimStates[node.id]?.textAlpha ?: 1f
                val zoomAlpha = ((animZoom - 0.5f) / 0.5f).coerceIn(0f, 1f)
                val finalAlpha = (nodeTextAlpha * zoomAlpha).coerceIn(0f, 1f)
                if (finalAlpha < 0.01f) continue

                drawText(
                    textLayoutResult = layout,
                    topLeft = screenPos - layout.half() + Offset(0f, nodeRadius * animZoom + textPadding),
                    color = fontColor,
                    alpha = finalAlpha
                )
            }
        }

        withTransform({
            scale(animZoom, animZoom)
            translate(center.x + movementOffset.x, center.y + movementOffset.y)
        }) {
            if (drawEdges) {
                val strokeNormal = 0.5f / animZoom
                val strokeHighlight = (0.5f + cursorCircleSizeKoef) / animZoom

                val dimmedLine = lerp(circleLineColor, circleLineColor.copy(alpha = GraphAnim.FADED_EDGE_ALPHA), selectionActiveAmount)

                for (i in nodes.indices) {
                    val sId = nodes[i].id
                    val sPos = coordinates[sId] ?: continue
                    val conns = connections[sId] ?: continue

                    for (j in conns.indices) {
                        val tId = conns[j]
                        val tPos = coordinates[tId] ?: continue

                        val minX = min(sPos.x, tPos.x)
                        val maxX = max(sPos.x, tPos.x)
                        val minY = min(sPos.y, tPos.y)
                        val maxY = max(sPos.y, tPos.y)
                        if (maxX < cullL || minX > cullR || maxY < cullT || minY > cullB) continue

                        val isSelected = sId == activeNodeId || tId == activeNodeId
                        val stroke = if (isSelected) {
                            strokeNormal + (strokeHighlight - strokeNormal) * selectionActiveAmount
                        } else {
                            strokeNormal
                        }
                        val edgeColor = if (isSelected) primaryColor else dimmedLine

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
                    val watchRadius = GraphNode.getCircleSize(circleRadius, connections[watchNodeId]?.size ?: 1, circleSizeMultiplier)
                    drawCircle(
                        color = primaryColor,
                        radius = watchRadius * 1.5f,
                        center = watchPos,
                        style = Stroke(width = 4f / animZoom)
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
                    exactPos, exactCol, exactTex, exactIdx, shader = buildShader
                )
            }

            drawCircle(color = Color.Green, radius = 1f / animZoom, center = Offset.Zero)
        }

        if (activeNodeId != null && activeNodeTextLayout != null && cursorTextAlpha > 0.01f) {
            val activePos = coordinates[activeNodeId]
            if (activePos != null) {
                val nodeRadius = GraphNode.getCircleSize(circleRadius, connections[activeNodeId]?.size ?: 1, circleSizeMultiplier)

                val bgPadX = 24f
                val bgPadY = 12f

                val screenX = (activePos.x + movementOffset.x) * animZoom + centerX
                val screenY = (activePos.y + movementOffset.y) * animZoom + centerY +
                        (nodeRadius * cursorCircleSizeKoef * animZoom + textPadding + bgPadY + (activeNodeTextLayout.size.height / 2f))

                val textTopLeft = Offset(screenX, screenY) - activeNodeTextLayout.half()

                val isLightText = fontColor.luminance() > 0.5f
                val pillBgColor = if (isLightText) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)

                drawRoundRect(
                    color = pillBgColor.copy(alpha = 0.85f * cursorTextAlpha),
                    topLeft = Offset(textTopLeft.x - bgPadX, textTopLeft.y - bgPadY),
                    size = Size(
                        width = activeNodeTextLayout.size.width + bgPadX * 2f,
                        height = activeNodeTextLayout.size.height + bgPadY * 2f
                    ),
                    cornerRadius = CornerRadius(24f, 24f)
                )

                drawText(
                    textLayoutResult = activeNodeTextLayout,
                    topLeft = textTopLeft,
                    color = fontColor,
                    alpha = cursorTextAlpha
                )
            }
        }
    }
}

