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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.engine.impl.ultra.UltraFastEngine
import com.moly3.dataviz.core.graph.hull.GroupHullController
import com.moly3.dataviz.core.graph.model.Connection
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphSettings
import com.moly3.dataviz.core.graph.model.GroupIndex
import com.moly3.dataviz.core.graph.model.GroupModel
import com.moly3.dataviz.graph.features.atlas.AtlasLayers
import com.moly3.gesture.PointerRequisite
import com.moly3.gesture.detectPointerTransformGestures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.set
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs

@OptIn(ExperimentalAtomicApi::class)
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

    groupModel: GroupModel<Id> = GroupModel.empty(),

    isImmediateReheatOnUpdate: Boolean = false,

    stateNodes: List<GraphNode<Id, Data>>,
    coordinates: Map<Id, Offset>,
    velocities: Map<Id, Offset>,
    connections: Map<Id, List<Connection<Id>>>,

    onPanDelta: (Offset) -> Unit,
    onWatchPosition: (Offset) -> Unit,
    onZoomChange: (Boolean, Float) -> Unit,
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

    val engineCoords = remember<HashMap<Id, Offset>> { HashMap() }
    val engineVels = remember { HashMap<Id, Offset>() }

    val lastEmittedCoords = remember { AtomicReference<Map<Id, Offset>?>(null) }
    var hasSeededThisMount by remember { mutableStateOf(false) }
    var mapVersion by remember { mutableIntStateOf(0) }

    val stateMutex = remember { Mutex() }

    val latestUserPosition by rememberUpdatedState(userPosition)
    val latestZoom by rememberUpdatedState(zoom)
    val latestSettings by rememberUpdatedState(settings)
    val latestNodes by rememberUpdatedState(stateNodes)
    val latestConnections by rememberUpdatedState(connections)
    val latestDragged by rememberUpdatedState(draggedNodeState)

    // Hull controller — survives recomposition, ties to a long-lived scope.
    val hullController = remember { GroupHullController(io) }
    DisposableEffect(hullController) {
        val hullScope = CoroutineScope(SupervisorJob() + io)
        hullController.start(hullScope)
        onDispose {
            hullController.stop()
            hullScope.cancel()
        }
    }
    remember(stateNodes.isNotEmpty()) {
        if (engineCoords.isEmpty() && stateNodes.isNotEmpty()) {
            for (node in stateNodes) {
                engineCoords[node.id] = coordinates[node.id] ?: Offset.Zero
                engineVels[node.id] = velocities[node.id] ?: Offset.Zero
            }
            mapVersion++
        }
        Unit
    }

    val hulls by hullController.hulls.collectAsState()

    val groupIndex = remember(groupModel) { GroupIndex.build(groupModel) }
    val groupIndexIdentity = remember(groupModel) { groupModel.hashCode() }
    val groupSettings = settings.groupSettings

    var hasSyncedGroupsThisMount by remember { mutableStateOf(false) }

    LaunchedEffect(engine, groupSettings, groupIndex, groupIndexIdentity) {
        engine.setGroupData(
            groupIndex = if (groupSettings.enabled) groupIndex else null,
            settings = groupSettings,
            groupIndexIdentity = groupIndexIdentity,
            suppressReheat = !hasSyncedGroupsThisMount,
        )
        hasSyncedGroupsThisMount = true
    }

    LaunchedEffect(groupModel, groupSettings, groupIndexIdentity) {
        if (!groupSettings.enabled) {
            hullController.submit(engine, groupIndex, groupSettings)
            return@LaunchedEffect
        }

        var waitedMs = 0
        val timeoutMs = 1000
        while (isActive &&
            waitedMs < timeoutMs &&
            !engine.hasSyncedGroupIndex(groupIndexIdentity)
        ) {
            delay(16L)
            waitedMs += 16
        }

        hullController.submit(engine, groupIndex, groupSettings)
    }

    LaunchedEffect(hullController, engine, groupSettings, groupIndex) {
        if (!groupSettings.enabled) return@LaunchedEffect
        while (isActive) {
            val interval = if (engine.isAsleep) groupSettings.hullSettledIntervalMs
            else groupSettings.hullRecomputeIntervalMs
            hullController.submit(engine, groupIndex, groupSettings)
            delay(interval)
        }
    }

    LaunchedEffect(watchNodeId) {
        if (watchNodeId != null) {
            launch(io) {
                while (isActive) {
                    val foundOffset = engineCoords[watchNodeId]
                    if (foundOffset != null) onWatchPosition(-foundOffset)
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

    val engineConnectionsRef = remember { arrayOf<Map<Id, List<Id>>>(emptyMap()) }
    val lastConnectionsRefHolder = remember { arrayOfNulls<Map<Id, List<Connection<Id>>>>(1) }

    LaunchedEffect(engine, latestSettings.view.targetFrameMs) {
        launch(io) {
            val coordsScratch = HashMap<Id, Offset>()
            val velsScratch = HashMap<Id, Offset>()
            var lastStructureSig = Int.MIN_VALUE

            // Yield once before the very first step. This lets Compose commit
            // the first composition (which already has the synchronously-seeded
            // coordinates) and present the first frame BEFORE we start the
            // expensive Barnes-Hut / spring / integration work. The user sees
            // the graph instantly; physics catches up over the next few frames.
            delay(16L)

            while (isActive) {
                val nodes = latestNodes
                if (nodes.isEmpty()) {
                    delay(100L)
                    continue
                }

                if (!latestSettings.isMoving && latestDragged == null) {
                    delay(100L)
                    continue
                }

                // Structural hash that catches edge rewires, not just count changes.
                // sumOf-of-sizes was identical for "delete one edge + add one edge",
                // so a rewire used to slip past unnoticed and the engine never woke.
                val conns = latestConnections
                val currentStructureSig = run {
                    var h = nodes.size
                    for (i in nodes.indices) {
                        val id = nodes[i].id
                        val list = conns[id]
                        h = h * 31 + id.hashCode()
                        if (list != null) {
                            h = h * 31 + list.size
                            for (c in list) h = h * 31 + c.target.hashCode()
                        } else {
                            h *= 31
                        }
                    }
                    h
                }
                val structureChanged = currentStructureSig != lastStructureSig

                if (!structureChanged && engine.isAsleep && latestDragged == null) {
                    delay(200L)
                    continue
                }
                lastStructureSig = currentStructureSig

                coordsScratch.clear()
                velsScratch.clear()
                stateMutex.withLock {
                    for (i in nodes.indices) {
                        val id = nodes[i].id
                        coordsScratch[id] = engineCoords[id] ?: Offset.Zero
                        velsScratch[id] = engineVels[id] ?: Offset.Zero
                    }
                }

                if (latestConnections !== lastConnectionsRefHolder[0]) {
                    engineConnectionsRef[0] = latestConnections.mapValues { (_, list) ->
                        list.map { it.target }
                    }
                    lastConnectionsRefHolder[0] = latestConnections
                }

                engine.step(
                    nodes,
                    engineConnectionsRef[0],
                    latestSettings.view,
                    coordsScratch,
                    velsScratch,
                    latestDragged,
                    isMoving = settings.isMoving
                )

                stateMutex.withLock {
                    var updated = false
                    for ((id, off) in coordsScratch) {
                        val prev = engineCoords[id]
                        if (prev == null ||
                            abs(prev.x - off.x) > 0.05f ||
                            abs(prev.y - off.y) > 0.05f
                        ) {
                            engineCoords[id] = off
                            updated = true
                        }
                    }
                    for ((id, vel) in velsScratch) engineVels[id] = vel

                    if (updated) mapVersion++
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        launch(io) {
            while (isActive) {
                delay(1000L)
                // Skip routine ticks while paused (and no drag in flight). The
                // end-of-drag save below is unconditional — a release must always
                // persist the final position.
                if (!latestSettings.isMoving && latestDragged == null) continue
                if (engine.isAsleep && latestDragged == null) continue

                var coordsCopy: HashMap<Id, Offset>? = null

                stateMutex.withLock {
                    if (engineCoords.isEmpty()) return@withLock
                    coordsCopy = HashMap(engineCoords)
                }

                coordsCopy?.let {
                    lastEmittedCoords.store(it)
                    onCoordinatesUpdate(it)
                }
            }
        }
    }

    LaunchedEffect(draggedNodeState) {
        if (draggedNodeState == null) {
            stateMutex.withLock {
                if (engineCoords.isEmpty()) return@withLock
                val snapshot = HashMap(engineCoords)
                lastEmittedCoords.store(snapshot)
                onCoordinatesUpdate(snapshot)
            }
        }
    }

    fun hitTest(tapOffset: Offset): GraphNode<Id, Data>? {
        // Rendering ADDS userPosition, so bounds testing must as well.
        val cameraOffset = latestUserPosition
        val circleSize = latestSettings.view.circleSize
        val multiplier = latestSettings.view.circleSizeMultiplier

        return latestNodes.lastOrNull { node ->
            val pos = engineCoords[node.id] ?: return@lastOrNull false
            val connCount = latestConnections[node.id]?.size ?: 1
            val radius = GraphNode.getCircleSize(circleSize, connCount, multiplier)

            val adjustedX = pos.x + cameraOffset.x
            val adjustedY = pos.y + cameraOffset.y

            // Fast AABB check
            if (abs(tapOffset.x - adjustedX) > radius || abs(tapOffset.y - adjustedY) > radius) {
                return@lastOrNull false
            }

            // Explicit geometric circle intersection
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
            detectPointerTransformGestures(
                consume = consume,
                numberOfPointers = 0,
                requisite = PointerRequisite.GreaterThan,
                onScrollChange = {
                    if (it.y != 0f) {
                        onZoomChange(false, it.y)
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

                    // Seed the node state with the initial offset immediately upon touch
                    hitTest(tapOffset)?.let {
                        draggedNodeState =
                            DragNodeData(it.id).copy(offset = tapOffset - latestUserPosition)
                    }
                },
                onGesture = { _, gesturePan, gestureZoom, _, _, pointerList ->
                    if (draggedNodeState != null && pointerList.size == 1) {
                        // drag handled via onCursorMove
                    } else {
                        if (watchNodeId == null && pointerList.size == 1) {
                            onPanDelta(gesturePan)
                        }
                        if (pointerList.size == 2 && gestureZoom != 1f) {
                            onZoomChange(true, gestureZoom)
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
        coordinates = engineCoords,
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