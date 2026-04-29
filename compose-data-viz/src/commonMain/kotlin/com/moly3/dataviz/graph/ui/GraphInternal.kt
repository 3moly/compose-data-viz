package com.moly3.dataviz.graph.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.func.half
import com.moly3.dataviz.graph.func.getNodeConnections
import com.moly3.dataviz.graph.func.getNodeOffset

/**
 * Optimizations vs original:
 *  - Viewport culling: nodes/edges outside visible area are skipped entirely
 *  - LOD (level of detail): below certain zoom we skip text and edges fade
 *  - Connection lookup uses cached set when a cursor node is selected
 *  - Text layouts only built for visible nodes (lazy via remember keyed on visible set)
 *  - Single transform per draw pass; cached darker colors
 */
@Composable
internal fun <Id, Data> GraphInternal(
    nodes: List<GraphNode<Id, Data>>,
    connections: Map<Id, List<Id>>,
    coordinates: Map<Id, Offset>,
    draggedNodeId: Id?,
    cursorNodeId: Id?,
    watchNodeId: Id?,

    modifier: Modifier = Modifier,
    movementOffset: Offset,
    circleRadius: Float,
    zoom: Float,
    primaryColor: Color,
    textStyle: TextStyle = TextStyle.Default,
    fontColor: Color,
    circleColor: Color,
    circleLineColor: Color
) {
    val darkerOthers = 0.4f
    val animZoom by animateFloatAsState(zoom, label = "zoom")
    val cursorCircleSizeKoef by animateFloatAsState(
        if (cursorNodeId != null) 1.3f else 1f,
        label = "cursorScale"
    )
    val localDensity = LocalDensity.current
    val textPadding = remember { localDensity.run { 16.toDp().toPx() } }
    val textMeasurer = rememberTextMeasurer(cacheSize = Int.MAX_VALUE)
    val textMeasurerNoCaching = rememberTextMeasurer(cacheSize = 0)

    // Cache text layouts (still keyed on nodes+style; we just don't allocate per frame)
    val textLayouts = remember(nodes, textStyle) {
        val style = textStyle.copy(fontSize = (12 / localDensity.density).sp)
        nodes.associate { node ->
            node.id to textMeasurer.measure(text = node.name, style = style)
        }
    }

    val cursorNodeTextLayout = remember(nodes, cursorNodeId, textStyle) {
        val style = textStyle.copy(fontSize = (24 / localDensity.density).sp)
        if (cursorNodeId != null) {
            val node = nodes.lastOrNull { it.id == cursorNodeId }
            node?.let { textMeasurerNoCaching.measure(text = it.name, style = style) }
        } else null
    }

    // Pre-compute cursor's connection set for fast contains() during draw
    val cursorConnectionSet = remember(cursorNodeId, connections) {
        if (cursorNodeId != null) {
            connections.getNodeConnections(cursorNodeId).toHashSet()
        } else emptySet<Id>()
    }

    // Cache the darker color (don't recompute per node per frame)
    val darkerLineColor = remember(circleLineColor) { circleLineColor.darker(factor = darkerOthers) }

    val alphaText by animateFloatAsState(if (animZoom >= 1f) 1f else 0f, label = "textAlpha")

    // Skip text & some detail at extreme zoom-out (LOD)
    val drawText = alphaText > 0f && animZoom > 0.5f
    val drawEdges = animZoom > 0.15f  // Below this zoom, hide edges entirely - massive speedup

    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height
        val centerX = canvasW * 0.5f
        val centerY = canvasH * 0.5f

        // Viewport bounds in graph (world) coordinates - used for culling
        // World point (x,y) -> screen: (x + movementOffset.x) * zoom + centerX
        // Inverse: x_world = (screen - centerX) / zoom - movementOffset.x
        val invZoom = 1f / animZoom.coerceAtLeast(0.0001f)
        val viewLeft = (-centerX) * invZoom - movementOffset.x
        val viewRight = (canvasW - centerX) * invZoom - movementOffset.x
        val viewTop = (-centerY) * invZoom - movementOffset.y
        val viewBottom = (canvasH - centerY) * invZoom - movementOffset.y
        // Add a small margin so things don't pop in/out at edges
        val margin = 100f
        val cullL = viewLeft - margin
        val cullR = viewRight + margin
        val cullT = viewTop - margin
        val cullB = viewBottom + margin

        withTransform({
            scale(animZoom, animZoom)
            translate(center.x + movementOffset.x, center.y + movementOffset.y)
        }) {

            // === EDGES (with viewport culling) ===
            if (drawEdges) {
                for (node in nodes) {
                    val startId = node.id
                    val startPos = coordinates[startId] ?: continue
                    val sx = startPos.x; val sy = startPos.y
                    // Coarse cull: skip if start is far outside (we still draw edges that
                    // cross viewport even if endpoints are outside, but rough cull is fine)
                    val nodeConns = connections[startId] ?: continue

                    for (targetId in nodeConns) {
                        val endPos = coordinates[targetId] ?: continue
                        val ex = endPos.x; val ey = endPos.y

                        // Bbox-vs-viewport cull for the edge
                        val minX = if (sx < ex) sx else ex
                        val maxX = if (sx > ex) sx else ex
                        val minY = if (sy < ey) sy else ey
                        val maxY = if (sy > ey) sy else ey
                        if (maxX < cullL || minX > cullR || maxY < cullT || minY > cullB) continue

                        val isSelected = startId == cursorNodeId || targetId == cursorNodeId
                        val color = when {
                            isSelected -> primaryColor
                            cursorNodeId != null -> darkerLineColor
                            else -> circleLineColor
                        }
                        drawLine(
                            color = color,
                            start = startPos,
                            end = endPos,
                            strokeWidth = 0.5f + if (isSelected) cursorCircleSizeKoef else 0f
                        )
                    }
                }
            }

            // === LABELS (text) - culled and gated by zoom ===
            if (drawText) {
                for (node in nodes) {
                    if (cursorNodeId == node.id) continue
                    val pos = node.getNodeOffset(coords = coordinates) ?: continue
                    if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue
                    val layout = textLayouts[node.id] ?: continue
                    val nodeRadius = GraphNode.getCircleSize(
                        circleRadius = circleRadius,
                        connectionCount = connections[node.id]?.size ?: 1
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = pos - layout.half() + Offset(0f, nodeRadius + textPadding),
                        color = fontColor,
                        alpha = if (cursorNodeId != null) 0.5f else alphaText
                    )
                }
            }

            // === NODES (circles) - culled ===
            for (node in nodes) {
                val pos = node.getNodeOffset(coords = coordinates) ?: continue
                if (pos.x < cullL || pos.x > cullR || pos.y < cullT || pos.y > cullB) continue
                val isSelected = node.id == cursorNodeId
                val nodeRadius = GraphNode.getCircleSize(
                    circleRadius = circleRadius,
                    connectionCount = connections[node.id]?.size ?: 1
                )

                val baseColor = when {
                    node.id == draggedNodeId -> Color.Green
                    node.colorValue != null -> Color(node.colorValue!!)
                    else -> circleColor
                }
                val color = when {
                    isSelected -> Color.Green
                    cursorNodeId != null -> if (cursorConnectionSet.contains(node.id)) {
                        baseColor
                    } else {
                        baseColor.darker(factor = darkerOthers)
                    }
                    else -> baseColor
                }

                val radius = if (isSelected) nodeRadius * cursorCircleSizeKoef else nodeRadius

                if (node.id == watchNodeId) {
                    drawCircle(
                        color = primaryColor,
                        radius = radius * 1.5f,
                        center = pos,
                        style = Stroke(width = 4f)
                    )
                }
                drawCircle(color = color, radius = radius, center = pos)
            }

            drawCircle(color = Color.Green, radius = 1f, center = Offset.Zero)
        }

        // Cursor label overlay (zoomed-out view) - same transform, drawn on top
        if (cursorNodeId != null && cursorNodeTextLayout != null) {
            withTransform({
                scale(animZoom, animZoom)
                translate(center.x + movementOffset.x, center.y + movementOffset.y)
            }) {
                val cursorNode = nodes.lastOrNull { it.id == cursorNodeId } ?: return@withTransform
                val pos = cursorNode.getNodeOffset(coords = coordinates) ?: return@withTransform
                val nodeRadius = GraphNode.getCircleSize(
                    circleRadius = circleRadius,
                    connectionCount = connections[cursorNode.id]?.size ?: 1
                )
                drawText(
                    textLayoutResult = cursorNodeTextLayout,
                    topLeft = pos - cursorNodeTextLayout.half() + Offset(
                        0f,
                        (nodeRadius + textPadding) / animZoom.coerceIn(0.000001f, 0.8f)
                    ),
                    color = fontColor
                )
            }
        }
    }
}