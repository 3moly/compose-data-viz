package com.moly3.dataviz.graph.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.graph.func.HybridGraphRenderer
import com.moly3.dataviz.graph.func.isNodeTapped
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.gesture.PointerRequisite
import com.moly3.gesture.detectPointerTransformGestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

data class DragNodeData<Id>(
    val id: Id,
    val offset: Offset? = null
)

@Composable
fun <Id, Data> Graph(
    modifier: Modifier = Modifier,
    userPosition: Offset,
    zoom: Float,

    stateNodes: List<GraphNode<Id, Data>>,
    coordinates: Map<Id, Offset>,
    velocities: Map<Id, Offset>,
    connections: Map<Id, List<Id>>,

    viewSettings: GraphViewSettings,
    onCentralGlobalPosition: (Offset) -> Unit,
    onZoomChange: (Float) -> Unit,
    watchNodeId: Id? = null,
    io: CoroutineContext,
    onNodeClick: (GraphNode<Id, Data>) -> Unit,
    onCoordinatesUpdate: (Map<Id, Offset>) -> Unit = {},
    onVelocitiesUpdate: (Map<Id, Offset>) -> Unit = {},
    primaryColor: Color,
    fontColor: Color,
    circleColor: Color,
    circleLineColor: Color
) {
    val scope = rememberCoroutineScope()
    var centerSizeState by remember { mutableStateOf(Offset(0.0F, 0.0F)) }
    var draggedNodeState by remember { mutableStateOf<DragNodeData<Id>?>(null) }
    var cursorNodeState by remember { mutableStateOf<GraphNode<Id, Data>?>(null) }

    val latestUserPosition by rememberUpdatedState(userPosition)
    val latestZoom by rememberUpdatedState(zoom)
    val latestViewSettings by rememberUpdatedState(viewSettings)
    val latestNodes by rememberUpdatedState(stateNodes)
    val connections by rememberUpdatedState(connections)
    val latestCoordinatesState by rememberUpdatedState(coordinates)
    val latestVelocitiesState by rememberUpdatedState(velocities)

    LaunchedEffect(watchNodeId) {
        if (watchNodeId != null) {
            launch(io) {
                while (true) {
                    val foundOffset = latestCoordinatesState[watchNodeId]
                    if (foundOffset != null)
                        onCentralGlobalPosition(foundOffset)
                    delay(3L)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        val renderer = HybridGraphRenderer<Id, Data>()
        launch(io) {
            while (true) {
                if (latestNodes.isEmpty()) {
                    delay(1_000L)
                } else {
                    val coordinates = latestCoordinatesState.toMutableMap()
                    val velocities = latestVelocitiesState.toMutableMap()
                    renderer.updateGraph(
                        latestNodes,
                        connections,
                        latestViewSettings,
                        coordinates,
                        velocities,
                        draggedNodeState
                    )
                    onCoordinatesUpdate(coordinates)
                    onVelocitiesUpdate(velocities)

                    delay(1L)
                }
            }
        }
    }

    GraphInternal(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                centerSizeState = Offset(it.size.width.toFloat(), it.size.height.toFloat()) / 2f
            }
            .pointerInput(watchNodeId) {
                detectPointerTransformGestures(
                    scope = scope,
                    numberOfPointers = 0,
                    requisite = PointerRequisite.GreaterThan,
                    onScrollChange = {
                        if (it.y != 0f) {
                            val newZoom = latestZoom * if (it.y > 0) 1.1f else 0.9f
                            onZoomChange(newZoom)
                        }
                    },
                    onClick = { position ->
                        scope.launch(io) {
                            val tapOffset = (position - centerSizeState) / latestZoom
                            val foundNode =
                                latestNodes.firstOrNull { node ->
                                    isNodeTapped(
                                        nodeOffset = latestCoordinatesState[node.id] ?: Offset.Zero,
                                        cameraOffset = -latestUserPosition,
                                        tapOffset = tapOffset,
                                        nodeRadius = GraphNode.getCircleSize(
                                            latestViewSettings.circleSize,
                                            connections[node.id]?.size ?: 1
                                        )
                                    )
                                }
                            if (foundNode != null) {
                                onNodeClick(foundNode)
                            }
                        }
                    },
                    onCursorMove = { position ->
                        scope.launch(io) {
                            val tapOffset = (position - centerSizeState) / latestZoom
                            val draggedNodeId = draggedNodeState
                            if (draggedNodeId != null) {
                                val moveOffset = tapOffset - latestUserPosition
                                draggedNodeState = draggedNodeState?.copy(offset = moveOffset)
                            } else {
                                val foundNode =
                                    latestNodes.firstOrNull { node ->

                                        isNodeTapped(
                                            nodeOffset = latestCoordinatesState[node.id]
                                                ?: Offset.Zero,
                                            cameraOffset = -latestUserPosition,
                                            tapOffset = tapOffset,
                                            nodeRadius = GraphNode.getCircleSize(
                                                latestViewSettings.circleSize,
                                                connections[node.id]?.size
                                                    ?: 1
                                            )
                                        )
                                    }
                                if (foundNode != null) {
                                    cursorNodeState = foundNode
                                } else {
                                    cursorNodeState = null
                                }
                            }
                        }
                    },
                    onGestureStart = { pointer ->
                        println("onGestureStart pointer: $pointer")
                        val tapOffset =
                            (pointer.position - centerSizeState) / latestZoom
                        val foundNode = latestNodes.firstOrNull { node ->
                            isNodeTapped(
                                nodeOffset = latestCoordinatesState[node.id] ?: Offset.Zero,
                                cameraOffset = -latestUserPosition,
                                tapOffset = tapOffset,
                                nodeRadius = GraphNode.getCircleSize(
                                    latestViewSettings.circleSize,
                                    connections[node.id]?.size
                                        ?: 1
                                )
                            )
                        }
                        if (foundNode != null) {
                            draggedNodeState = DragNodeData(foundNode.id)
                        }
                    },
                    onGesture = { gestureCentroid: Offset,
                                  gesturePan: Offset,
                                  gestureZoom: Float,
                                  gestureRotate: Float,
                                  mainPointerInputChange: PointerInputChange,
                                  pointerList: List<PointerInputChange> ->
                        val newScale = latestZoom * gestureZoom
                        val dragged = draggedNodeState
                        if (dragged != null && pointerList.size == 1) {
                        } else {
                            if (watchNodeId == null) {
                                onCentralGlobalPosition((gesturePan / latestZoom))
                            }

                            if (pointerList.size == 2) {
                                onZoomChange(newScale)
                            }
                        }
                    },
                    onGestureEnd = {
                        draggedNodeState = null
                        scope.launch(io) {
                            while (draggedNodeState != null) {
                                draggedNodeState = null
                                delay(50L)
                            }
                        }
                    },
                    onGestureCancel = {
                        draggedNodeState = null
                        scope.launch(io) {
                            while (draggedNodeState != null) {
                                draggedNodeState = null
                                delay(50L)
                            }
                        }
                    }
                )
            }
            .clip(RoundedCornerShape(0.dp)),

        nodes = latestNodes,
        coordinates = coordinates,
        connections = connections,

        draggedNodeId = draggedNodeState?.id,
        cursorNodeId = cursorNodeState?.id,

        movementOffset = userPosition,
        zoom = zoom,
        circleRadius = viewSettings.circleSize,
        watchNodeId = watchNodeId,
        primaryColor = primaryColor,
        circleColor = circleColor,
        circleLineColor = circleLineColor,
        fontColor = fontColor
    )
}