package com.moly3.dataviz.graph.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.graph.func.getNodeConnections
import com.moly3.dataviz.graph.func.getNodeOffset
import com.moly3.dataviz.func.half
import com.moly3.dataviz.core.graph.model.GraphNode

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
    val zoom by animateFloatAsState(zoom)
    val cursorCircleSizeKoef by animateFloatAsState(if (cursorNodeId != null) 1.3f else 1f)
    val localDensity = LocalDensity.current
    val textPadding = remember() {
        localDensity.run { (16).toDp().toPx() }
    }
    val textMeasurer = rememberTextMeasurer(cacheSize = Int.MAX_VALUE)
    val textMeasurerNoCaching = rememberTextMeasurer(cacheSize = 0)

    val textLayouts = remember(nodes, textStyle) {
        val style = textStyle.copy(fontSize = (12 / localDensity.density).sp)
        nodes.associate { node ->
            node.id to textMeasurer.measure(
                text = node.name,
                style = style
            )
        }
    }

    val cursorNodeTextLayout = remember(nodes, cursorNodeId, textStyle) {
        val style = textStyle.copy(fontSize = (24 / localDensity.density).sp)
        if (cursorNodeId != null) {
            val node = nodes.lastOrNull { it.id == cursorNodeId }
            if (node != null) {
                textMeasurerNoCaching.measure(
                    text = node.name,
                    style = style
                )
            } else null
        } else null
    }

    val alphaText by animateFloatAsState(if (zoom >= 1f) 1f else 0f)

    Canvas(modifier = modifier) {
        withTransform({
            scale(zoom, zoom)
            translate(center.x + movementOffset.x, center.y + movementOffset.y)
        }) {
            // Draw connections directly
            nodes.forEach { node ->
                val startId = node.id
                val startPos = coordinates[startId] ?: return@forEach

                connections.getNodeConnections(startId).forEach { targetId ->
                    val endPos = coordinates[targetId] ?: return@forEach

                    val isSelected = startId == cursorNodeId || targetId == cursorNodeId
                    val color = if (isSelected) {
                        primaryColor
                    } else {
                        if (cursorNodeId != null) {
                            circleLineColor.darker(factor = darkerOthers)
                        } else {
                            circleLineColor
                        }
                    }

                    drawLine(
                        color = color,
                        start = startPos,
                        end = endPos,
                        strokeWidth = 0.5f + if (isSelected) cursorCircleSizeKoef else 0f
                    )
                }
            }

            if (alphaText > 0f) {
                nodes.forEach { node ->
                    if (cursorNodeId == node.id) return@forEach
                    val nodeCenter = node.getNodeOffset(coords = coordinates) ?: return@forEach
                    val textLayoutResult = textLayouts[node.id] ?: return@forEach
                    val nodeRadius = GraphNode.getCircleSize(
                        circleRadius = circleRadius,
                        connectionCount = connections[node.id]?.size ?: 1
                    )

                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = nodeCenter - textLayoutResult.half() + Offset(
                            0f,
                            nodeRadius + textPadding
                        ),
                        color = fontColor,
                        alpha = if (cursorNodeId != node.id && cursorNodeId != null) 0.5f else alphaText
                    )
                }
            }

            nodes.forEach { node ->
                val nodeCenter = node.getNodeOffset(coords = coordinates) ?: return@forEach
                val isSelected = node.id == cursorNodeId
                val nodeRadius = GraphNode.getCircleSize(
                    circleRadius = circleRadius,
                    connectionCount = connections[node.id]?.size ?: 1
                )

                val nodeColor = when {
                    node.id == draggedNodeId -> Color.Green
                    node.colorValue != null -> Color(node.colorValue!!)
                    else -> circleColor
                }

                val color = if (isSelected) {
                    Color.Green
                } else {
                    if (cursorNodeId != null) {
                        val nodeConnections = connections.getNodeConnections(cursorNodeId)
                        if (nodeConnections.contains(node.id)) {
                            nodeColor
                        } else {
                            nodeColor.darker(factor = darkerOthers)
                        }
                    } else {
                        nodeColor
                    }
                }

                val radius = if (isSelected) {
                    nodeRadius * cursorCircleSizeKoef
                } else {
                    nodeRadius
                }

                if (node.id == watchNodeId) {
                    drawCircle(
                        color = primaryColor,
                        radius = radius * 1.5f,
                        center = nodeCenter,
                        style = Stroke(width = 4f)
                    )
                }
                drawCircle(
                    color = color,
                    radius = radius,
                    center = nodeCenter
                )
            }

            drawCircle(
                color = Color.Green,
                radius = 1f,
                center = Offset.Zero
            )
        }
        withTransform({
            scale(zoom, zoom)
            translate(center.x + movementOffset.x, center.y + movementOffset.y)
        }) {
            if (cursorNodeId != null && cursorNodeTextLayout != null) {
                val cursorNode = nodes.lastOrNull { it.id == cursorNodeId }
                if (cursorNode != null) {
                    val nodeCenter =
                        cursorNode.getNodeOffset(coords = coordinates) ?: return@withTransform
                    val nodeRadius = GraphNode.getCircleSize(
                        circleRadius = circleRadius,
                        connectionCount = connections[cursorNode.id]?.size ?: 1
                    )

                    drawText(
                        textLayoutResult = cursorNodeTextLayout,
                        topLeft = nodeCenter - cursorNodeTextLayout.half() + Offset(
                            0f,
                            (nodeRadius + textPadding) / zoom.coerceIn(0.000001f, 0.8f)
                        ),
                        color = fontColor
                    )
                }
            }
        }
    }
}