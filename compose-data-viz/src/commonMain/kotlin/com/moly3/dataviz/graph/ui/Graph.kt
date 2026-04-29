package com.moly3.dataviz.graph.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.dataviz.graph.func.HybridGraphRenderer
import com.moly3.dataviz.graph.func.InitialLayout
import com.moly3.dataviz.graph.func.isNodeTapped
import com.moly3.gesture.PointerRequisite
import com.moly3.gesture.detectPointerTransformGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

data class DragNodeData<Id>(
    val id: Id,
    val offset: Offset? = null
)

@Composable
fun <Id, Data> Graph(
    modifier: Modifier = Modifier,
    consume: Boolean,
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
    circleLineColor: Color,
    textStyle: TextStyle = TextStyle.Default,
) {
    val scope = rememberCoroutineScope()
    var centerSizeState by remember { mutableStateOf(Offset(0f, 0f)) }
    var draggedNodeState by remember { mutableStateOf<DragNodeData<Id>?>(null) }
    var cursorNodeState by remember { mutableStateOf<GraphNode<Id, Data>?>(null) }

    val liveCoordinates = remember { mutableStateMapOf<Id, Offset>() }
    val liveVelocities = remember { HashMap<Id, Offset>() }

    // Track which "graph identity" we've already initialized — when this changes
    // we run InitialLayout for the new nodes. Recomputing on every recomposition
    // would be wasteful and would also fight with the live physics state.
    var lastLayoutKey by remember { mutableStateOf<Int?>(null) }

    val latestUserPosition by rememberUpdatedState(userPosition)
    val latestZoom by rememberUpdatedState(zoom)
    val latestViewSettings by rememberUpdatedState(viewSettings)
    val latestNodes by rememberUpdatedState(stateNodes)
    val latestConnections by rememberUpdatedState(connections)
    val latestDragged by rememberUpdatedState(draggedNodeState)

    // === INITIAL LAYOUT ===
    // The moment the node set changes (first appearance, or a meaningful add/remove)
    // we run a synchronous layout pass on the IO context and seed liveCoordinates BEFORE
    // the canvas tries to draw. This is what makes nodes appear pre-arranged instead of
    // exploding outward from (0,0).
    LaunchedEffect(stateNodes) {
        if (stateNodes.isEmpty()) return@LaunchedEffect

        // Cheap fingerprint of the graph: count + xor of hashes. Avoids running layout
        // again when only metadata (colour, name) changes.
        val key = stateNodes.size xor stateNodes.fold(0) { acc, n -> acc xor n.id.hashCode() }
        if (key == lastLayoutKey) {
            for (node in stateNodes) {
                if (node.id !in liveCoordinates) {
                    liveCoordinates[node.id] = coordinates[node.id] ?: Offset.Zero
                    liveVelocities[node.id] = velocities[node.id] ?: Offset.Zero
                }
            }
            return@LaunchedEffect
        }

        // Compute initial layout on a worker thread to keep recomposition cheap.
        val seeded = withContext(Dispatchers.Default) {
            InitialLayout.compute(
                nodes = stateNodes,
                connections = connections,
                settings = viewSettings,
                existingCoordinates = coordinates  // honor caller-supplied positions
            )
        }

        // Atomic batch update — single recomposition pulse instead of N.
        // We also drop coords/velocities for removed nodes here.
        val newIds = stateNodes.map { it.id }.toHashSet()
        liveCoordinates.keys.retainAll(newIds)
        liveVelocities.keys.retainAll(newIds)
        for (node in stateNodes) {
            // Prefer caller-supplied non-zero positions; fall back to seeded layout.
            val seed = seeded[node.id] ?: Offset.Zero
            liveCoordinates[node.id] = seed
            // Velocities start at zero — gives the simulation a clean slate to polish.
            liveVelocities[node.id] = Offset.Zero
        }
        lastLayoutKey = key
    }

    LaunchedEffect(watchNodeId) {
        if (watchNodeId != null) {
            launch(io) {
                while (true) {
                    val foundOffset = liveCoordinates[watchNodeId]
                    if (foundOffset != null) onCentralGlobalPosition(foundOffset)
                    delay(16L)
                }
            }
        }
    }

    LaunchedEffect(latestViewSettings.targetFrameMs) {
        val renderer = HybridGraphRenderer<Id, Data>()
        val targetFrameMs = latestViewSettings.targetFrameMs
        val settledFrameMs = 250L
        var settledFrames = 0

        launch(io) {
            val coordsScratch = HashMap<Id, Offset>()
            val velsScratch = HashMap<Id, Offset>()

            while (true) {
                val nodes = latestNodes
                if (nodes.isEmpty()) {
                    delay(500L)
                    continue
                }
                // Wait for initial layout to seed positions before running physics —
                // otherwise we'd run the simulation on an empty map and produce zeros.
                if (liveCoordinates.size < nodes.size) {
                    delay(8L)
                    continue
                }

                coordsScratch.clear()
                velsScratch.clear()
                for (node in nodes) {
                    coordsScratch[node.id] = liveCoordinates[node.id] ?: Offset.Zero
                    velsScratch[node.id] = liveVelocities[node.id] ?: Offset.Zero
                }

                val frameStart = TimeSource.Monotonic.markNow()
                renderer.updateGraph(
                    nodes,
                    latestConnections,
                    latestViewSettings,
                    coordsScratch,
                    velsScratch,
                    latestDragged
                )
                val elapsed = frameStart.elapsedNow().inWholeMilliseconds

                for ((id, off) in coordsScratch) {
                    val prev = liveCoordinates[id]
                    if (prev == null || prev.x != off.x || prev.y != off.y) {
                        liveCoordinates[id] = off
                    }
                }
                for ((id, vel) in velsScratch) {
                    liveVelocities[id] = vel
                }

                val isSettled = renderer.isSettled() && latestDragged == null
                if (isSettled) settledFrames++ else settledFrames = 0
                val targetMs = if (settledFrames > 30) settledFrameMs else targetFrameMs
                delay((targetMs - elapsed).coerceAtLeast(1L))
            }
        }
    }

    LaunchedEffect(Unit) {
        launch(io) {
            while (true) {
                delay(1000L)
                if (liveCoordinates.isNotEmpty()) {
                    onCoordinatesUpdate(HashMap(liveCoordinates))
                    onVelocitiesUpdate(HashMap(liveVelocities))
                }
            }
        }
    }
    LaunchedEffect(draggedNodeState) {
        if (draggedNodeState == null && liveCoordinates.isNotEmpty()) {
            onCoordinatesUpdate(HashMap(liveCoordinates))
            onVelocitiesUpdate(HashMap(liveVelocities))
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
                    consume = consume,
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
                            val foundNode = latestNodes.lastOrNull { node ->
                                isNodeTapped(
                                    nodeOffset = liveCoordinates[node.id] ?: Offset.Zero,
                                    cameraOffset = -latestUserPosition,
                                    tapOffset = tapOffset,
                                    nodeRadius = GraphNode.getCircleSize(
                                        latestViewSettings.circleSize,
                                        latestConnections[node.id]?.size ?: 1
                                    )
                                )
                            }
                            if (foundNode != null) onNodeClick(foundNode)
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
                                val foundNode = latestNodes.lastOrNull { node ->
                                    isNodeTapped(
                                        nodeOffset = liveCoordinates[node.id] ?: Offset.Zero,
                                        cameraOffset = -latestUserPosition,
                                        tapOffset = tapOffset,
                                        nodeRadius = GraphNode.getCircleSize(
                                            latestViewSettings.circleSize,
                                            latestConnections[node.id]?.size ?: 1
                                        )
                                    )
                                }
                                cursorNodeState = foundNode
                            }
                        }
                    },
                    onGestureStart = { pointer ->
                        val tapOffset = (pointer.position - centerSizeState) / latestZoom
                        val foundNode = latestNodes.lastOrNull { node ->
                            isNodeTapped(
                                nodeOffset = liveCoordinates[node.id] ?: Offset.Zero,
                                cameraOffset = -latestUserPosition,
                                tapOffset = tapOffset,
                                nodeRadius = GraphNode.getCircleSize(
                                    latestViewSettings.circleSize,
                                    latestConnections[node.id]?.size ?: 1
                                )
                            )
                        }
                        if (foundNode != null) draggedNodeState = DragNodeData(foundNode.id)
                    },
                    onGesture = { _, gesturePan, gestureZoom, _, _, pointerList ->
                        val newScale = latestZoom * gestureZoom
                        val dragged = draggedNodeState
                        if (dragged != null && pointerList.size == 1) {
                            // dragging — pan suppressed
                        } else {
                            if (watchNodeId == null) onCentralGlobalPosition(gesturePan / latestZoom)
                            if (pointerList.size == 2) onZoomChange(newScale)
                        }
                    },
                    onGestureEnd = { draggedNodeState = null },
                    onGestureCancel = { draggedNodeState = null }
                )
            }
            .clip(RoundedCornerShape(0.dp)),

        nodes = latestNodes,
        coordinates = liveCoordinates,
        connections = latestConnections,

        draggedNodeId = draggedNodeState?.id,
        cursorNodeId = cursorNodeState?.id,

        movementOffset = userPosition,
        zoom = zoom,
        circleRadius = viewSettings.circleSize,
        watchNodeId = watchNodeId,
        primaryColor = primaryColor,
        circleColor = circleColor,
        circleLineColor = circleLineColor,
        fontColor = fontColor,
        textStyle = textStyle
    )
}