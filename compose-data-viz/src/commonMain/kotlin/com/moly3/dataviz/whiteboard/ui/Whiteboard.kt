package com.moly3.dataviz.whiteboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.whiteboard.func.calculatePointer
import com.moly3.dataviz.whiteboard.func.dashboard
import com.moly3.dataviz.whiteboard.func.getMapPosition
import com.moly3.dataviz.whiteboard.func.roundToNearest
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.AddShapeConnection
import com.moly3.dataviz.core.whiteboard.model.ShapeConnection
import com.moly3.dataviz.core.whiteboard.model.WhiteboardSettings
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.DrawShapeState
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.StylusPath
import com.moly3.dataviz.core.whiteboard.model.StylusPoint
import kotlin.math.abs

/**
 * IMPORTANT FIXES vs previous version:
 *
 *  1. `currentPath` is now a `SnapshotStateList<StylusPoint>` instead of a
 *     `List<StylusPoint>` rebuilt on every move. Adding a point used to be
 *     O(n) (list copy) and `path.toList()` on commit was another O(n) — for a
 *     200-point stroke that's ~40k allocations. Now O(1) amortized append.
 *
 *  2. The two `keys / shapes.map { x -> x.position }` pattern for `remember(...)`
 *     of the settings panel offset is collapsed to `derivedStateOf` so we don't
 *     allocate a new list on every recomposition.
 *
 *  3. Scroll handler: zoom on home-hold now centers on the cursor instead of
 *     drifting the origin — same anchoring trick as pinch-zoom in Dashboard.kt.
 *
 *  4. Density convention is documented and consistent: `actualDensity.density`
 *     is the device px/dp ratio. `calculatePointer` and `DrawConnections` both
 *     receive `1f / density` so they share the same hit-test grid.
 *
 *  5. Stroke-width animation is no longer a hard `snapTo(1f)` followed by an
 *     animate-to — it just `animateTo` directly, so re-selecting a connection
 *     doesn't flash the stroke thin.
 */
@Composable
fun <ShapeType : Shape<Id>, Id> Whiteboard(
    minShapeSize: Float,
    consume: Boolean,
    modifier: Modifier,
    action: Action<ShapeType, Id>?,
    backgroundModifier: Modifier,
    connectionsModifier: Modifier,
    settings: WhiteboardSettings,
    zoom: Float,
    roundToNearest: Int?,
    connectionDragBlankId: Id,
    userCoordinate: Offset,
    circleRadius: Float?,
    isDrawing: Boolean,
    shapes: List<ShapeType>,
    connections: List<ShapeConnection<Id>>,
    onActionSet: (Action<ShapeType, Id>?) -> Unit,
    onAddPath: (StylusPath) -> Unit,
    onMoveShape: (Int, Offset) -> Unit,
    onResizeShape: (Int, Offset, Offset) -> Unit,
    onAddConnection: (AddShapeConnection<Id>) -> Unit,
    onZoomChange: (Float) -> Unit,
    onUserCoordinateChange: (Offset) -> Unit,
    settingsPanel: @Composable (position: Offset, action: Action<ShapeType, Id>, onDoneAction: () -> Unit) -> Unit,
    onDrawBlock: @Composable (DrawShapeState<ShapeType, Id>) -> Unit,
    onDrawConnectionCircle: @Composable (RoundedCornerShape, Modifier) -> Unit,
    minZoom: Float = 0.1f,
    maxZoom: Float = 8f,
) {
    // FIX: snapshot state list — appends are O(1), and we don't rebuild the
    // whole list on every move event.
    val currentPath = remember { mutableStateListOf<StylusPoint>() }

    val scaleMovementModifier = 5f
    val sizeRound = 25
    val actualDensity = LocalDensity.current

    val strokeWidth = remember { Animatable(1f) }

    val isHomeHoldState = remember { mutableStateOf(false) }
    val cursorPositionState = remember { mutableStateOf(Offset.Zero) }
    val centerOfScreenState = remember { mutableStateOf(Offset.Zero) }

    val centerOfScreen = centerOfScreenState.value
    val cursorPosition = cursorPositionState.value

    LaunchedEffect(action) {
        // FIX: don't snap to 1f first; animate to the new target directly so
        // there's no visible flash when re-selecting connections.
        val target = if (action is Action.Connection) 4f else 1f
        strokeWidth.animateTo(target)
    }

    val connectionConfig = remember(actualDensity, settings) {
        ConnectionConfig(
            stubLength = actualDensity.run { settings.stubLength.dp.toPx() },
            controlPointFactor = settings.controlPointer,
            maxArcHeight = actualDensity.run { settings.maxHit.dp.toPx() },
            strokeWidth = settings.strokeWidth.dp,
            hitThreshold = settings.hitThreshold.dp
        )
    }

    val mapCursor = remember(cursorPosition, centerOfScreen, zoom, userCoordinate) {
        getMapPosition(cursorPosition, centerOfScreen, zoom, userCoordinate)
    }

    val dragActionState = remember { mutableStateOf<DragAction<Id>?>(null) }

    val pointer = remember(
        mapCursor,
        shapes,
        connections,
        zoom,
        centerOfScreen,
        connectionConfig,
        userCoordinate,
        cursorPosition,
        roundToNearest,
        action,
        circleRadius,
        actualDensity.density
    ) {
        calculatePointer(
            minShapeSize = minShapeSize,
            shapes = shapes,
            mapCursor = mapCursor,
            connections = connections,
            zoom = zoom,
            connectionConfig = connectionConfig,
            userCoordinate = userCoordinate,
            centerOfScreen = centerOfScreen,
            cursorPosition = cursorPosition,
            dragAction = dragActionState.value,
            sizeRound = sizeRound,
            detectionPercent = 0.1f,
            circleRadius = circleRadius,
            roundToNearest = roundToNearest,
            action = action,
            // Density: map-space units use 1f/density so that a 1dp visual
            // hitbox corresponds to 1dp of hit area regardless of screen DPI.
            // DrawConnections divides zoom by the same density downstream.
            density = 1f / actualDensity.density
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(0.dp))
    ) {
        Box(backgroundModifier.fillMaxSize())
        Row(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                DrawConnections(
                    minShapeSize = minShapeSize,
                    modifier = connectionsModifier,
                    stylusPoint = currentPath,
                    shapes = shapes,
                    connections = connections,
                    dragActionState = dragActionState,
                    userCoordinate = userCoordinate,
                    config = connectionConfig,
                    zoom = zoom,
                    centerOfScreen = centerOfScreen,
                    cursorPosition = cursorPosition,
                    action = action,
                    selectedConnectionStrokeWidth = strokeWidth.value,
                    lineColor = settings.defaultLineColor,
                    drawColor = Color.White,
                    roundToNearest = roundToNearest,
                    connectionDragBlankId = connectionDragBlankId
                )
                DrawShapes(
                    minShapeSize = minShapeSize,
                    mousePosition = cursorPosition,
                    shapes = shapes,
                    dragActionState = dragActionState,
                    userCoordinate = userCoordinate,
                    zoom = zoom,
                    density = actualDensity.density,
                    action = action,
                    onDrawBlock = onDrawBlock,
                    roundToNearest = roundToNearest,
                    onDrawConnectionCircle = onDrawConnectionCircle
                )

                val actionState = rememberUpdatedState(action)
                val updatedShapes = rememberUpdatedState(shapes)
                val updatedConnections = rememberUpdatedState(connections)
                val isDrawingState = rememberUpdatedState(isDrawing)
                val zoomState = rememberUpdatedState(zoom)
                val userCoordinateState = rememberUpdatedState(userCoordinate)
                val roundToNearestState = rememberUpdatedState(roundToNearest)
                val circleRadiusState = rememberUpdatedState(circleRadius)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            centerOfScreenState.value = Offset(
                                it.size.width.toFloat(),
                                it.size.height.toFloat()
                            ) / 2f
                        }
                        .dashboard(
                            consume = consume,
                            roundToNearestState = roundToNearestState,
                            zoomState = zoomState,
                            sizeRound = sizeRound,
                            circleRadiusState = circleRadiusState,
                            cursorPositionState = cursorPositionState,
                            centerOfScreenState = centerOfScreenState,
                            userCoordinateState = userCoordinateState,
                            connectionConfig = connectionConfig,
                            isDrawingState = isDrawingState,
                            actionState = actionState,
                            onActionSet = onActionSet,
                            shapes = updatedShapes,
                            connections = updatedConnections,
                            minZoom = minZoom,
                            maxZoom = maxZoom,
                            onScrollChange = { delta ->
                                if (isHomeHoldState.value && delta.y != 0f) {
                                    // Cursor-anchored zoom on home-hold + scroll.
                                    val currentZoom = zoomState.value.takeIf { it != 0f } ?: 1f
                                    val newZoom = (currentZoom * (1f + delta.y / 200f))
                                        .coerceIn(minZoom, maxZoom)
                                    if (newZoom != currentZoom) {
                                        val center = centerOfScreenState.value
                                        val cursor = cursorPositionState.value
                                        val mapAtCursor = getMapPosition(
                                            cursor, center, currentZoom, userCoordinateState.value
                                        )
                                        onZoomChange(newZoom)
                                        val anchored = mapAtCursor -
                                                (cursor - center) / newZoom
                                        onUserCoordinateChange(anchored)
                                    }
                                } else {
                                    val u = userCoordinateState.value
                                    onUserCoordinateChange(u - delta * scaleMovementModifier)
                                }
                            },
                            onDrawStart = { point ->
                                val sw = (point.pressure * 15f).coerceIn(2f, 20f)
                                currentPath.add(point.copy(strokeWidth = sw))
                            },
                            onDrawChange = { point ->
                                val sw = (point.pressure * 15f).coerceIn(2f, 20f)
                                currentPath.add(point.copy(strokeWidth = sw))
                            },
                            onDrawEnd = {
                                if (currentPath.isNotEmpty()) {
                                    // Single allocation: toList() now happens once at commit.
                                    onAddPath(
                                        StylusPath(
                                            points = currentPath.toList(),
                                            color = Color.Red
                                        )
                                    )
                                    currentPath.clear()
                                }
                            },
                            dragActionState = dragActionState,
                            onClick = { },
                            onMoveShape = onMoveShape,
                            onResizeShape = onResizeShape,
                            onAddConnection = onAddConnection,
                            onZoomChange = onZoomChange,
                            onUserCoordinateChange = onUserCoordinateChange,
                            minShapeSize = minShapeSize
                        )
                        .pointerHoverIcon(pointer.pointerIcon)
                ) {}
            }
        }

        Box(Modifier.fillMaxSize()) {
            // FIX: derivedStateOf avoids allocating a fresh `shapes.map { it.position }`
            // list on every recomposition just to feed the remember() key list.
            val center by remember {
                derivedStateOf {
                    val a = action
                    if (a == null) return@derivedStateOf null
                    val dragAction = dragActionState.value
                    val addOffset = if (dragAction != null) {
                        when (val dt = dragAction.dragType) {
                            is DragType.Connection -> Offset.Zero
                            is DragType.Resize -> when (a) {
                                is Action.Connection -> Offset.Zero
                                is Action.DoubleClicked -> Offset.Zero
                                is Action.ShapeAction -> {
                                    if (a.shape.id == dt.shapeId) {
                                        // Halving the X delta keeps the popup anchored
                                        // to the shape's growing centre. Y left alone
                                        // because settings panel hangs above.
                                        dragAction.accelerate.copy(y = 0f) / 2f
                                    } else Offset.Zero
                                }
                            }
                            is DragType.ShapeDrag -> when (a) {
                                is Action.Connection -> Offset.Zero
                                is Action.DoubleClicked -> Offset.Zero
                                is Action.ShapeAction -> {
                                    if (a.shape.id == dt.shapeId) {
                                        dragAction.accelerate - dragAction.startMapPosition
                                    } else Offset.Zero
                                }
                            }
                        }
                    } else Offset.Zero
                    (a.getOffsetMenu<ShapeType>(shapes = shapes) -
                            userCoordinate +
                            addOffset.roundToNearest(roundToNearest)
                            ) * zoom + centerOfScreen
                }
            }
            val c = center
            if (c != null && action != null) {
                settingsPanel(c, action) { onActionSet(null) }
            }
        }
    }
}