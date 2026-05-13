package com.moly3.dataviz.graph.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.engine.impl.ultra.UltraFastEngine
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.graph.func.InitialLayout
import com.moly3.dataviz.graph.func.isNodeTapped
import com.moly3.gesture.PointerRequisite
import com.moly3.gesture.detectPointerTransformGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.collections.set
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs

/**
 * Interactive force-directed graph.
 *
 * All visual + behavioural tuning lives on [settings] — see [GraphSettings] for the full
 * surface and sensible defaults. Pass `GraphSettings.Default` to get started.
 */
@Composable
fun <Id, Data> Graph(
    modifier: Modifier = Modifier,
    settings: GraphSettings = GraphSettings.Default,
    engine: IGraphEngine<Id, Data> = remember { UltraFastEngine() },

    consume: Boolean,
    userPosition: Offset,
    zoom: Float,

    isImmediateReheatOnUpdate: Boolean = false,

    stateNodes: List<GraphNode<Id, Data>>,
    coordinates: Map<Id, Offset>,
    velocities: Map<Id, Offset>,
    connections: Map<Id, List<Id>>,

    onCentralGlobalPosition: (Offset) -> Unit,
    onZoomChange: (Float) -> Unit,
    watchNodeId: Id? = null,
    io: CoroutineContext,
    onNodeClick: (GraphNode<Id, Data>) -> Unit,
    onCoordinatesUpdate: (Map<Id, Offset>) -> Unit = {},
    onVelocitiesUpdate: (Map<Id, Offset>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var centerSizeState by remember { mutableStateOf(Offset.Zero) }
    var draggedNodeState by remember { mutableStateOf<DragNodeData<Id>?>(null) }
    var cursorNodeState by remember { mutableStateOf<GraphNode<Id, Data>?>(null) }

    val liveCoordinates = remember { mutableStateMapOf<Id, Offset>() }
    val liveVelocities = remember { HashMap<Id, Offset>() }

    val stateMutex = remember { Mutex() }
    var lastLayoutKey by remember { mutableStateOf<Int?>(null) }

    val latestUserPosition by rememberUpdatedState(userPosition)
    val latestZoom by rememberUpdatedState(zoom)
    val latestSettings by rememberUpdatedState(settings)
    val latestNodes by rememberUpdatedState(stateNodes)
    val latestConnections by rememberUpdatedState(connections)
    val latestDragged by rememberUpdatedState(draggedNodeState)

    // === INITIAL LAYOUT ===
    LaunchedEffect(stateNodes) {
        if (stateNodes.isEmpty()) return@LaunchedEffect

        val key = stateNodes.size xor stateNodes.fold(0) { acc, n -> acc xor n.id.hashCode() }
        if (key == lastLayoutKey) {
            stateMutex.withLock {
                for (i in stateNodes.indices) {
                    val id = stateNodes[i].id
                    if (id !in liveCoordinates) {
                        liveCoordinates[id] = coordinates[id] ?: Offset.Zero
                        liveVelocities[id] = velocities[id] ?: Offset.Zero
                    }
                }
            }
            return@LaunchedEffect
        }

        val seeded = withContext(Dispatchers.Default) {
            InitialLayout.compute(
                nodes = stateNodes,
                connections = connections,
                settings = settings.view,
                existingCoordinates = coordinates
            )
        }

        stateMutex.withLock {
            Snapshot.withMutableSnapshot {
                val newIds = stateNodes.map { it.id }.toHashSet()
                liveCoordinates.keys.retainAll(newIds)
                liveVelocities.keys.retainAll(newIds)

                for (i in stateNodes.indices) {
                    val id = stateNodes[i].id
                    liveCoordinates[id] = seeded[id] ?: Offset.Zero
                    if (id !in liveVelocities) {
                        liveVelocities[id] = Offset.Zero
                    }
                }
            }
        }
        lastLayoutKey = key
    }

    LaunchedEffect(watchNodeId) {
        if (watchNodeId != null) {
            launch(io) {
                while (isActive) {
                    val foundOffset = liveCoordinates[watchNodeId]
                    if (foundOffset != null) onCentralGlobalPosition(foundOffset)
                    delay(16L)
                }
            }
        }
    }

    if(isImmediateReheatOnUpdate){
        LaunchedEffect(stateNodes, connections, settings.view) {
            engine.reheat()
        }
    }


    // === PHYSICS LOOP ===
    LaunchedEffect(engine, latestSettings.view.targetFrameMs) {
        launch(io) {
            val coordsScratch = HashMap<Id, Offset>()
            val velsScratch = HashMap<Id, Offset>()
            var lastStructureSig = -1

            while (isActive) {
                val nodes = latestNodes
                if (nodes.isEmpty()) {
                    delay(100L)
                    continue
                }

                val currentStructureSig =
                    nodes.size * 31 + latestConnections.values.sumOf { it.size }
                val structureChanged = currentStructureSig != lastStructureSig

                if (!structureChanged && engine.isAsleep && latestDragged == null) {
                    delay(200L)
                    continue
                }
                lastStructureSig = currentStructureSig

                withFrameNanos { }

                coordsScratch.clear(); velsScratch.clear()
                stateMutex.withLock {
                    for (i in nodes.indices) {
                        val id = nodes[i].id
                        coordsScratch[id] = liveCoordinates[id] ?: Offset.Zero
                        velsScratch[id] = liveVelocities[id] ?: Offset.Zero
                    }
                }

                engine.step(
                    nodes,
                    latestConnections,
                    latestSettings.view,
                    coordsScratch, velsScratch, latestDragged
                )

                stateMutex.withLock {
                    Snapshot.withMutableSnapshot {
                        for ((id, off) in coordsScratch) {
                            val prev = liveCoordinates[id]
                            if (prev == null ||
                                abs(prev.x - off.x) > 0.05f ||
                                abs(prev.y - off.y) > 0.05f
                            ) {
                                liveCoordinates[id] = off
                            }
                        }
                    }
                    for ((id, vel) in velsScratch) liveVelocities[id] = vel
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        launch(io) {
            while (isActive) {
                delay(1000L)
                if (engine.isAsleep && latestDragged == null) continue

                var coordsCopy: HashMap<Id, Offset>? = null
                var velsCopy: HashMap<Id, Offset>? = null

                stateMutex.withLock {
                    if (liveCoordinates.isEmpty()) return@withLock
                    coordsCopy = HashMap(liveCoordinates)
                    velsCopy = HashMap(liveVelocities)
                }

                if (coordsCopy != null && velsCopy != null) {
                    onCoordinatesUpdate(coordsCopy)
                    onVelocitiesUpdate(velsCopy)
                }
            }
        }
    }

    LaunchedEffect(draggedNodeState) {
        if (draggedNodeState == null) {
            stateMutex.withLock {
                if (liveCoordinates.isEmpty()) return@withLock
                onCoordinatesUpdate(HashMap(liveCoordinates))
                onVelocitiesUpdate(HashMap(liveVelocities))
            }
        }
    }

    // Hit-test helper. Re-evaluated against latest state on every call.
    fun hitTest(tapOffset: Offset): GraphNode<Id, Data>? {
        val cameraOffset = -latestUserPosition
        val circleSize = latestSettings.view.circleSize
        val multiplier = latestSettings.view.circleSizeMultiplier
        return latestNodes.lastOrNull { node ->
            val connCount = latestConnections[node.id]?.size ?: 1
            isNodeTapped(
                nodeOffset = liveCoordinates[node.id] ?: Offset.Zero,
                cameraOffset = cameraOffset,
                tapOffset = tapOffset,
                nodeRadius = GraphNode.getCircleSize(circleSize, connCount, multiplier)
            )
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
                            val zoomCfg = latestSettings.zoom
                            val factor = if (it.y > 0) zoomCfg.stepIn else zoomCfg.stepOut
                            val newZoom =
                                (latestZoom * factor).coerceIn(zoomCfg.minZoom, zoomCfg.maxZoom)
                            if (newZoom != latestZoom) onZoomChange(newZoom)
                        }
                    },
                    onClick = { position ->
                        scope.launch(io) {
                            val tapOffset = (position - centerSizeState) / latestZoom
                            hitTest(tapOffset)?.let(onNodeClick)
                        }
                    },
                    onCursorMove = { position ->
                        scope.launch(io) {
                            val tapOffset = (position - centerSizeState) / latestZoom
                            if (draggedNodeState != null) {
                                draggedNodeState =
                                    draggedNodeState?.copy(offset = tapOffset - latestUserPosition)
                            } else {
                                cursorNodeState = hitTest(tapOffset)
                            }
                        }
                    },
                    onGestureStart = { pointer ->
                        val tapOffset = (pointer.position - centerSizeState) / latestZoom
                        hitTest(tapOffset)?.let { draggedNodeState = DragNodeData(it.id) }
                    },
                    onGesture = { _, gesturePan, gestureZoom, _, _, pointerList ->
                        if (draggedNodeState == null || pointerList.size != 1) {
                            if (watchNodeId == null) {
                                if (abs(gesturePan.x) > 0.5f || abs(gesturePan.y) > 0.5f) {
                                    onCentralGlobalPosition(gesturePan / latestZoom)
                                }
                            }
                            if (pointerList.size == 2 && abs(1f - gestureZoom) > 0.005f) {
                                val zoomCfg = latestSettings.zoom
                                val newScale =
                                    (latestZoom * gestureZoom).coerceIn(
                                        zoomCfg.minZoom,
                                        zoomCfg.maxZoom
                                    )
                                if (newScale != latestZoom) onZoomChange(newScale)
                            }
                        }
                    },
                    onGestureEnd = { draggedNodeState = null },
                    onGestureCancel = { draggedNodeState = null }
                )
            }
            .clip(RoundedCornerShape(0.dp)),

        settings = settings,
        nodes = latestNodes,
        coordinates = liveCoordinates,
        connections = latestConnections,
        draggedNodeId = draggedNodeState?.id,
        cursorNodeId = cursorNodeState?.id,
        movementOffset = userPosition,
        zoom = zoom,
        watchNodeId = watchNodeId,
    )
}