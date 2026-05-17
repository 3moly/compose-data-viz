package com.moly3.dataviz.graph.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.engine.impl.ultra.UltraFastEngine
import com.moly3.dataviz.core.graph.hull.GroupHullController
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.graph.features.atlas.AtlasLayers
import com.moly3.dataviz.graph.func.InitialLayout
import com.moly3.gesture.PointerRequisite
import com.moly3.gesture.detectPointerTransformGestures
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.collections.set
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun <Id, Data> Graph(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    settings: GraphSettings = GraphSettings.Default,
    engine: IGraphEngine<Id, Data> = remember { UltraFastEngine() },
    consume: Boolean,
    userPosition: Offset,
    zoom: Float,

    atlasLayers: AtlasLayers = AtlasLayers.EMPTY,
    getIconKey: (Id, Data) -> String? = { _, _ -> null },

    getNodeGroups: (Id, Data) -> List<String> = { _, _ -> emptyList() },
    getGroupColor: (String) -> Color = { Color(0x4D00BFFF) },
    getGroupName: (String) -> String = { "group label" },

    isImmediateReheatOnUpdate: Boolean = false,

    stateNodes: List<GraphNode<Id, Data>>,
    coordinates: Map<Id, Offset>,
    velocities: Map<Id, Offset>,
    connections: Map<Id, List<Id>>,

    onCentralGlobalPosition: (Boolean, Offset) -> Unit,
    onZoomChange: (Float) -> Unit,
    watchNodeId: Id? = null,
    io: CoroutineContext,
    onNodeClick: (GraphNode<Id, Data>) -> Unit,
    onCoordinatesUpdate: (Map<Id, Offset>) -> Unit = {},
    customPopup: (@Composable (node: GraphNode<Id, Data>) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var centerSizeState by remember { mutableStateOf(Offset.Zero) }
    var draggedNodeState by remember { mutableStateOf<DragNodeData<Id>?>(null) }
    var cursorNodeState by remember { mutableStateOf<GraphNode<Id, Data>?>(null) }

    val liveCoordinates = remember<HashMap<Id, Offset>> { HashMap() }
    val liveVelocities = remember { HashMap<Id, Offset>() }

    var mapVersion by remember { mutableIntStateOf(0) }

    val stateMutex = remember { Mutex() }
    var lastLayoutKey by remember { mutableStateOf<Int?>(null) }

    val latestUserPosition by rememberUpdatedState(userPosition)
    val latestZoom by rememberUpdatedState(zoom)
    val latestSettings by rememberUpdatedState(settings)
    val latestNodes by rememberUpdatedState(stateNodes)
    val latestConnections by rememberUpdatedState(connections)
    val latestDragged by rememberUpdatedState(draggedNodeState)

    // Hull controller — survives recomposition, ties to a long-lived scope.
    val hullController = remember { GroupHullController(io) }
    DisposableEffect(hullController) {
        val scope = CoroutineScope(SupervisorJob() + io)
        hullController.start(scope)
        onDispose {
            hullController.stop()
            scope.cancel()
        }
    }

    val hulls by hullController.hulls.collectAsState()
    val groupResolver: (Int) -> List<String> = remember(stateNodes) {
        val snapshot = stateNodes  // capture once
        { i ->
            if (i in snapshot.indices) {
                val node = snapshot[i]
                getNodeGroups(node.id, node.data)
            } else {
                emptyList()
            }
        }
    }
    val groupSettings = settings.groupSettings
    // Push group data into the engine BEFORE each step. The engine reads it
// inside syncData(). Since this is just two reference writes, doing it on
// recomposition is fine.
    LaunchedEffect(engine, groupSettings, stateNodes) {
        engine.setGroupData(
            groupsForNodeIndex = if (groupSettings.enabled) groupResolver else null,
            settings = groupSettings,
        )
    }
// Drive hull recompute on a configurable cadence.
// We rebuild more often while the engine is hot, then idle out.
    LaunchedEffect(hullController, engine, groupSettings, getGroupColor) {
        if (!groupSettings.enabled) return@LaunchedEffect
//        val ultra = engine as? UltraFastEngine<Id, Data> ?: return@LaunchedEffect
        val ultra = engine
        while (isActive) {
            val interval = if (ultra.isAsleep) groupSettings.hullSettledIntervalMs
            else groupSettings.hullRecomputeIntervalMs
            hullController.submit(ultra, getGroupName, getGroupColor, groupSettings)
            delay(interval)
        }
    }

    LaunchedEffect(stateNodes, coordinates) {
        if (stateNodes.isEmpty()) return@LaunchedEffect

        // --- one-shot seed from saved coordinates, the moment they're available ---
//        if (liveCoordinates.isEmpty() && coordinates.values.any { it != Offset.Zero }) {
//            stateMutex.withLock {
//                for (node in stateNodes) {
//                    liveCoordinates[node.id] = coordinates[node.id] ?: Offset.Zero
//                    liveVelocities[node.id] = velocities[node.id] ?: Offset.Zero
//                }
//                mapVersion++
//            }
//            lastLayoutKey = stateNodes.size xor stateNodes.fold(0) { a, n -> a xor n.id.hashCode() }
//            engine.nudge()          // wake it so the seeded layout actually renders/settles
//            return@LaunchedEffect
//        }

        // --- existing path: no saved positions, compute a fresh layout ---
        val key = stateNodes.size xor stateNodes.fold(0) { acc, n -> acc xor n.id.hashCode() }
        if (key == lastLayoutKey) {
            stateMutex.withLock {
                var updated = false
                for (node in stateNodes) {
                    if (node.id !in liveCoordinates) {
                        liveCoordinates[node.id] = coordinates[node.id] ?: Offset.Zero
                        liveVelocities[node.id] = velocities[node.id] ?: Offset.Zero
                        updated = true
                    }
                }
                if (updated) mapVersion++
            }
            return@LaunchedEffect
        }

        val seeded = withContext(Dispatchers.Default) {
            if (coordinates.isEmpty()) {
                // loose scatter — give physics something to untangle
                stateNodes.associate {
                    it.id to Offset(
                        (Random.nextFloat() - 0.5f) * 800f,
                        (Random.nextFloat() - 0.5f) * 800f,
                    )
                }
            } else {
                InitialLayout.compute(stateNodes, connections, settings.view, coordinates)
            }
        }
        stateMutex.withLock {
            val newIds = stateNodes.map { it.id }.toHashSet()
            liveCoordinates.keys.retainAll(newIds)
            liveVelocities.keys.retainAll(newIds)
            var updated = false
            for (node in stateNodes) {
                val seedOffset = seeded[node.id] ?: Offset.Zero
                if (liveCoordinates[node.id] != seedOffset) {
                    liveCoordinates[node.id] = seedOffset
                    updated = true
                }
                if (node.id !in liveVelocities) liveVelocities[node.id] = Offset.Zero
            }
            if (updated) mapVersion++
        }
        lastLayoutKey = key
    }

    LaunchedEffect(watchNodeId) {
        if (watchNodeId != null) {
            launch(io) {
                while (isActive) {
                    val foundOffset = liveCoordinates[watchNodeId]
                    if (foundOffset != null) onCentralGlobalPosition(true, foundOffset)
                    delay(16L)
                }
            }
        }
    }

    if (isImmediateReheatOnUpdate) {
        LaunchedEffect(stateNodes, connections, settings.view) {
            engine.reheat()
        }
    }

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

                coordsScratch.clear();
                velsScratch.clear()
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
                    coordsScratch,
                    velsScratch,
                    latestDragged
                )

                stateMutex.withLock {
                    var updated = false
                    for ((id, off) in coordsScratch) {
                        val prev = liveCoordinates[id]
                        if (prev == null ||
                            abs(prev.x - off.x) > 0.05f ||
                            abs(prev.y - off.y) > 0.05f
                        ) {
                            liveCoordinates[id] = off
                            updated = true
                        }
                    }
                    for ((id, vel) in velsScratch) liveVelocities[id] = vel

                    if (updated) mapVersion++
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
                }
            }
        }
    }

    LaunchedEffect(draggedNodeState) {
        if (draggedNodeState == null) {
            stateMutex.withLock {
                if (liveCoordinates.isEmpty()) return@withLock
                onCoordinatesUpdate(HashMap(liveCoordinates))
            }
        }
    }

    fun hitTest(tapOffset: Offset): GraphNode<Id, Data>? {
        // Removed the negative sign. Rendering ADDS userPosition, so bounds testing must as well.
        val cameraOffset = latestUserPosition
        val circleSize = latestSettings.view.circleSize
        val multiplier = latestSettings.view.circleSizeMultiplier

        return latestNodes.lastOrNull { node ->
            val pos = liveCoordinates[node.id] ?: return@lastOrNull false
            val connCount = latestConnections[node.id]?.size ?: 1
            val radius = GraphNode.getCircleSize(circleSize, connCount, multiplier)

            val adjustedX = pos.x + cameraOffset.x
            val adjustedY = pos.y + cameraOffset.y

            // Fast AABB check
            if (abs(tapOffset.x - adjustedX) > radius || abs(tapOffset.y - adjustedY) > radius) {
                return@lastOrNull false
            }

            // Replaced black-box `isNodeTapped` with explicit geometric circle intersection
            val dx = tapOffset.x - adjustedX
            val dy = tapOffset.y - adjustedY
            (dx * dx + dy * dy) <= (radius * radius)
        }
    }

    val graphModifier = modifier
        .fillMaxSize()
        .onGloballyPositioned {
            centerSizeState = Offset(it.size.width.toFloat(), it.size.height.toFloat()) / 2f
        }
        .pointerInput(watchNodeId) {
//            var localSyncZoom = latestZoom
            detectPointerTransformGestures(
                consume = consume,
                numberOfPointers = 0,
                requisite = PointerRequisite.GreaterThan,
                onScrollChange = {
                    if (it.y != 0f) {
//                        val zoomCfg = latestSettings.zoom
//                        val factor = if (it.y > 0) zoomCfg.stepIn else zoomCfg.stepOut
//                        localSyncZoom =
//                            (localSyncZoom * factor).coerceIn(zoomCfg.minZoom, zoomCfg.maxZoom)
                        onZoomChange(it.y)
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
//                    localSyncZoom = latestZoom
                    val tapOffset = (pointer.position - centerSizeState) / latestZoom

                    // Seed the node state with the initial offset immediately upon touch
                    hitTest(tapOffset)?.let {
                        draggedNodeState =
                            DragNodeData(it.id).copy(offset = tapOffset - latestUserPosition)
                    }
                },
                onGesture = { centroid, gesturePan, gestureZoom, _, pointer, pointerList ->
//                    println("gestureZoom: ${pointer.type} ${gestureZoom} ${localSyncZoom}")
                    if (draggedNodeState != null && pointerList.size == 1) {
//                        val tapOffset = (centroid - centerSizeState) / localSyncZoom
//                        draggedNodeState = draggedNodeState?.copy(offset = tapOffset - latestUserPosition)
                    } else {
                        if (watchNodeId == null && pointerList.size == 1) {
                            onCentralGlobalPosition(false, gesturePan)
                        }
                        if (pointerList.size == 2 && gestureZoom != 1f) {
//                            val zoomCfg = latestSettings.zoom
//                            localSyncZoom = (localSyncZoom * gestureZoom).coerceIn(
//                                zoomCfg.minZoom,
//                                zoomCfg.maxZoom
//                            )
//                            onZoomChange(localSyncZoom)
                        }
                    }
                },
                onGestureEnd = { draggedNodeState = null },
                onGestureCancel = { draggedNodeState = null }
            )
        }
        .clip(RoundedCornerShape(0.dp))
    GraphInternal(
        atlasLayers = atlasLayers,
        getIconKey = getIconKey,

        textStyle = textStyle,
        customPopup = customPopup,
        modifier = graphModifier,
        settings = settings,
        nodes = latestNodes,
        coordinates = liveCoordinates,
        coordinatesVersion = mapVersion,
        connections = latestConnections,
        draggedNodeId = draggedNodeState?.id,
        cursorNodeId = cursorNodeState?.id,
        movementOffset = userPosition,
        zoom = zoom,
        watchNodeId = watchNodeId,

        hulls = hulls,
        groupSettings = groupSettings,
    )
}