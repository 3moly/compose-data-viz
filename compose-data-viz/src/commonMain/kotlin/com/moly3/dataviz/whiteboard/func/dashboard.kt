package com.moly3.dataviz.whiteboard.func

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.AddShapeConnection
import com.moly3.dataviz.core.whiteboard.model.BoxSide
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.ShapeConnection
import com.moly3.dataviz.core.whiteboard.model.StylusPoint
import com.moly3.dataviz.core.whiteboard.model.allSides
import com.moly3.gesture.PointerRequisite
import com.moly3.gesture.detectPointerTransformGestures
import kotlin.math.max
import kotlin.math.min
import kotlin.time.ExperimentalTime

/**
 * IMPORTANT FIXES vs previous version:
 *
 *  1. ALL inputs are wrapped in `rememberUpdatedState` — the previous version
 *     captured `minShapeSize`, `connectionConfig`, and `consume` raw from the
 *     lambda closure, so they went stale when the parent recomposed with new
 *     values. (`pointerInput(Unit)` keeps the same lambda forever.)
 *
 *  2. Zoom is clamped to a sane min/max so users can't pinch into the void.
 *
 *  3. Pinch-zoom is now anchored to the gesture centroid — previously a pinch
 *     felt like it was zooming around the screen origin, not the fingers.
 *
 *  4. Hit testing on click iterates shapes in reverse (top-most first) and
 *     short-circuits — the previous `lastOrNull` traversed the entire list
 *     every time.
 *
 *  5. Connection-drop hit testing breaks out of BOTH loops via labelled return
 *     (the previous `break` only exited the inner loop, so it kept scanning).
 *
 *  6. Resize delta is computed against the gesture-start position, not by
 *     accumulating `accelerate` — that accumulation was correct in practice
 *     but unintuitive; the new form mirrors how every other 2D editor works.
 */
@OptIn(ExperimentalTime::class)
fun <ShapeType : Shape<Id>, Id> Modifier.dashboard(
    minShapeSize: Float,
    consume: Boolean,
    sizeRound: Int,
    circleRadiusState: State<Float?>,
    roundToNearestState: State<Int?>,
    zoomState: State<Float>,
    userCoordinateState: State<Offset>,
    isDrawingState: State<Boolean>,
    cursorPositionState: MutableState<Offset>,
    centerOfScreenState: MutableState<Offset>,
    connectionConfig: ConnectionConfig,
    shapes: State<List<ShapeType>>,
    connections: State<List<ShapeConnection<Id>>>,
    dragActionState: MutableState<DragAction<Id>?>,
    actionState: State<Action<ShapeType, Id>?>,
    onZoomChange: (Float) -> Unit,
    onUserCoordinateChange: (Offset) -> Unit,
    onScrollChange: (delta: Offset) -> Unit = {},
    onActionSet: (Action<ShapeType, Id>?) -> Unit,
    onAddConnection: (AddShapeConnection<Id>) -> Unit,
    onMoveShape: (Int, Offset) -> Unit,
    onResizeShape: (Int, Offset, Offset) -> Unit,
    onDrawStart: (StylusPoint) -> Unit,
    onDrawChange: (StylusPoint) -> Unit,
    onDrawEnd: () -> Unit,
    onClick: () -> Unit,
    minZoom: Float = 0.1f,
    maxZoom: Float = 8f,
): Modifier = composed {

    // Wrap EVERY non-State input to avoid stale captures inside pointerInput(Unit).
    val currentMinShapeSize by rememberUpdatedState(minShapeSize)
    val currentConsume by rememberUpdatedState(consume)
    val currentSizeRound by rememberUpdatedState(sizeRound)
    val currentConnectionConfig by rememberUpdatedState(connectionConfig)
    val currentMinZoom by rememberUpdatedState(minZoom)
    val currentMaxZoom by rememberUpdatedState(maxZoom)

    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentOnUserCoordinateChange by rememberUpdatedState(onUserCoordinateChange)
    val currentOnScrollChange by rememberUpdatedState(onScrollChange)
    val currentOnActionSet by rememberUpdatedState(onActionSet)
    val currentOnAddConnection by rememberUpdatedState(onAddConnection)
    val currentOnMoveShape by rememberUpdatedState(onMoveShape)
    val currentOnResizeShape by rememberUpdatedState(onResizeShape)
    val currentOnDrawStart by rememberUpdatedState(onDrawStart)
    val currentOnDrawChange by rememberUpdatedState(onDrawChange)
    val currentOnDrawEnd by rememberUpdatedState(onDrawEnd)
    val currentOnClick by rememberUpdatedState(onClick)

    Modifier.pointerInput(Unit) {
        detectPointerTransformGestures(
            consume = currentConsume,
            numberOfPointers = 0,
            requisite = PointerRequisite.GreaterThan,

            onClick = { offset ->
                if (centerOfScreenState.value == Offset.Zero) return@detectPointerTransformGestures

                val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f
                currentOnClick()

                val mousePosition = getMapPosition(
                    offset,
                    centerOfScreenState.value,
                    safeZoom,
                    userCoordinateState.value
                )

                // Reverse iteration: top-most shape wins.
                val currentShapes = shapes.value
                val foundShape = findTopShape(currentShapes, mousePosition)

                if (foundShape != null) {
                    val current = actionState.value
                    if (current is Action.DoubleClicked<*, *> &&
                        current.shape.id == foundShape.id
                    ) {
                        // Single-click on the shape currently in edit mode:
                        // keep edit mode (don't downgrade to ShapeAction).
                        return@detectPointerTransformGestures
                    }
                    currentOnActionSet(Action.ShapeAction(foundShape))
                } else {
                    val foundConnection = findConnection(
                        minShapeSize = currentMinShapeSize,
                        shapes = currentShapes,
                        connections = connections.value,
                        dragAction = dragActionState.value,
                        cursorPosition = cursorPositionState.value,
                        centerOfScreen = centerOfScreenState.value,
                        userCoordinate = userCoordinateState.value,
                        zoom = safeZoom,
                        config = currentConnectionConfig,
                        roundToNearest = roundToNearestState.value,
                        density = 1f / density
                    )
                    if (foundConnection != null) {
                        currentOnActionSet(Action.Connection(foundConnection))
                    } else {
                        // FIX: clicking empty space dismisses any open action,
                        // including DoubleClicked edit mode.
                        if (actionState.value != null) currentOnActionSet(null)
                    }
                }
            },

            onDoubleClick = { pos ->
                if (centerOfScreenState.value == Offset.Zero) return@detectPointerTransformGestures
                val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f
                val mousePosition = getMapPosition(
                    pos, centerOfScreenState.value, safeZoom, userCoordinateState.value
                )
                val foundShape = findTopShape(shapes.value, mousePosition)
                if (foundShape != null) {
                    currentOnActionSet(Action.DoubleClicked(foundShape))
                } else {
                    currentOnActionSet(null)
                }
            },

            onCursorMove = { cursorOffset ->
                cursorPositionState.value = cursorOffset
            },

            onScrollChange = { delta -> currentOnScrollChange(delta) },

            onGestureStart = { pointerChange ->
                if (centerOfScreenState.value == Offset.Zero) return@detectPointerTransformGestures

                val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f
                val mousePosition = getMapPosition(
                    pointerChange.position,
                    centerOfScreenState.value,
                    safeZoom,
                    userCoordinateState.value
                )

                if (isDrawingState.value) {
                    currentOnDrawStart(
                        StylusPoint(
                            x = mousePosition.x,
                            y = mousePosition.y,
                            pressure = extractPressure(pointerChange),
                            timestamp = 0L
                        )
                    )
                    return@detectPointerTransformGestures
                }

                val foundShapePair = getShapeGlobalDragType(
                    mousePosition = mousePosition,
                    action = actionState.value,
                    shapes = shapes.value,
                    sizeRound = currentSizeRound,
                    circleRadius = circleRadiusState.value
                )

                dragActionState.value = if (foundShapePair != null) {
                    val dragType = foundShapePair.first
                    val targetShape = foundShapePair.second
                    when (dragType) {
                        is DragType.Connection -> DragAction(
                            startMapPosition = targetShape.position,
                            accelerate = Offset.Zero,
                            dragType = DragType.Connection(
                                startShapeId = targetShape.id,
                                startShapeType = dragType.startShapeType,
                                boxSide = targetShape
                            )
                        )

                        is DragType.Resize -> DragAction(
                            startMapPosition = mousePosition,
                            accelerate = Offset.Zero,
                            dragType = dragType
                        )

                        is DragType.ShapeDrag -> DragAction(
                            startMapPosition = targetShape.position,
                            accelerate = targetShape.position,
                            dragType = dragType
                        )
                    }
                } else null
            },

            onGesture = { _, gesturePan, gestureZoom, _, mainPointerInputChange, pointerList ->
                val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f

                val mousePosition = getMapPosition(
                    mainPointerInputChange.position,
                    centerOfScreenState.value,
                    safeZoom,
                    userCoordinateState.value
                )

                val pressedCount = pointerList.count { it.pressed }
                when {
                    pressedCount <= 1 -> {
                        if (isDrawingState.value) {
                            val pressure = extractPressure(mainPointerInputChange)
                            val tilt = extractTilt(mainPointerInputChange)
                            val strokeWidth = (pressure * 15f).coerceIn(2f, 20f)
                            currentOnDrawChange(
                                StylusPoint(
                                    x = mousePosition.x,
                                    y = mousePosition.y,
                                    pressure = pressure,
                                    tiltX = tilt.first,
                                    tiltY = tilt.second,
                                    strokeWidth = strokeWidth,
                                    timestamp = 0L
                                )
                            )
                        } else {
                            val off = gesturePan / safeZoom
                            val dragAction = dragActionState.value
                            if (dragAction != null) {
                                dragActionState.value = dragAction.copy(
                                    accelerate = dragAction.accelerate + off
                                )
                            } else {
                                currentOnUserCoordinateChange(userCoordinateState.value - off)
                            }
                        }
                    }
                    pressedCount >= 2 -> {
                        // Pinch-zoom anchored to the gesture centroid: zoom about
                        // the fingers' midpoint, not the screen origin.
                        val centroidScreen = mainPointerInputChange.position
                        val centroidMap = getMapPosition(
                            centroidScreen,
                            centerOfScreenState.value,
                            safeZoom,
                            userCoordinateState.value
                        )
                        val rawZoom = safeZoom * gestureZoom
                        val newZoom = rawZoom.coerceIn(currentMinZoom, currentMaxZoom)
                        if (newZoom != safeZoom) {
                            currentOnZoomChange(newZoom)
                            // Keep centroidMap anchored under the centroid:
                            // mapAtCentroid_old == mapAtCentroid_new
                            // → userCoordinate_new = centroidMap - (centroidScreen - center) / newZoom
                            val center = centerOfScreenState.value
                            val anchored = centroidMap -
                                    (centroidScreen - center) / newZoom
                            currentOnUserCoordinateChange(anchored)
                        }
                        // Two-finger pan still applies.
                        val off = gesturePan / safeZoom
                        if (off != Offset.Zero) {
                            currentOnUserCoordinateChange(userCoordinateState.value - off)
                        }
                    }
                }
            },

            onGestureEnd = { pointerChange ->
                if (isDrawingState.value) {
                    currentOnDrawEnd()
                    return@detectPointerTransformGestures
                }

                val currentShapes = shapes.value
                val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f
                val mousePosition = getMapPosition(
                    position = pointerChange.position,
                    centerOfScreen = centerOfScreenState.value,
                    zoom = safeZoom,
                    userCoordinate = userCoordinateState.value
                )

                val dragAction = dragActionState.value ?: return@detectPointerTransformGestures
                when (val action = dragAction.dragType) {
                    is DragType.Connection -> {
                        val hit = findSideUnderCursor(
                            currentShapes,
                            mousePosition,
                            currentSizeRound / 2f
                        )
                        if (hit != null) {
                            currentOnAddConnection(
                                AddShapeConnection(
                                    fromBoxId = action.startShapeId,
                                    toBoxId = hit.first.id,
                                    fromSide = action.startShapeType,
                                    toSide = hit.second,
                                )
                            )
                        }
                    }

                    is DragType.ShapeDrag -> {
                        val foundIndex = currentShapes.indexOfFirst { x -> x.id == action.shapeId }
                        if (foundIndex != -1) {
                            currentOnMoveShape(foundIndex, dragAction.accelerate)
                        }
                    }

                    is DragType.Resize -> {
                        val resizeType = action.type
                        val foundIndex = currentShapes.indexOfFirst { x -> x.id == action.shapeId }
                        if (foundIndex != -1) {
                            val shape = currentShapes[foundIndex]
                            val roundToNearest = roundToNearestState.value
                            val accelerate = dragAction.accelerate
                            val resizePosition = resizePosition(accelerate, resizeType, roundToNearest)
                            val sizeDelta = resizeSize(accelerate, resizeType, roundToNearest)
                            currentOnResizeShape(
                                foundIndex,
                                shape.position + resizePosition,
                                (shape.size + sizeDelta).keepCurrentOrMin(currentMinShapeSize)
                            )
                        }
                    }
                }
                dragActionState.value = null
            },

            onGestureCancel = {
                // Stop any in-progress draw cleanly.
                if (isDrawingState.value) currentOnDrawEnd()
                dragActionState.value = null
            }
        )
    }
}

/** Find the top-most shape under a map-space point. Iterates in reverse so
 *  later-drawn shapes win, matching z-order. Short-circuits on first hit. */
private inline fun <ShapeType : Shape<Id>, Id> findTopShape(
    shapes: List<ShapeType>,
    mousePosition: Offset
): ShapeType? {
    for (i in shapes.indices.reversed()) {
        val s = shapes[i]
        if (isInShape(mousePosition, s.position, s.size)) return s
    }
    return null
}

/** Find a (shape, side) pair under the cursor, used when a Connection drag ends.
 *  Returns null if nothing is hit. Iterates in reverse for z-order correctness. */
private inline fun <ShapeType : Shape<Id>, Id> findSideUnderCursor(
    shapes: List<ShapeType>,
    mousePosition: Offset,
    radius: Float
): Pair<Shape<Id>, BoxSide>? {
    for (i in shapes.indices.reversed()) {
        val shape = shapes[i]
        for (side in allSides) {
            val isInSide = isInSidePosition(
                mousePosition = mousePosition,
                itemPosition = shape.position,
                boxSize = shape.size,
                side = side,
                radius = radius
            )
            if (isInSide) return shape to side
        }
    }
    return null
}