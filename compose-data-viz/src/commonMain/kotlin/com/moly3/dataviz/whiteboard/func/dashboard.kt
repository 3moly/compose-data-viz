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
import kotlin.time.ExperimentalTime

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
): Modifier = composed {

    // Wrap properties and lambdas to prevent stale captures in pointerInput(Unit)
    val currentConsume by rememberUpdatedState(consume)
    val currentSizeRound by rememberUpdatedState(sizeRound)
    val currentConnectionConfig by rememberUpdatedState(connectionConfig)

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
                if (centerOfScreenState.value != Offset.Zero) {
                    val currentShapes = shapes.value
                    val currentConnections = connections.value
                    val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f

                    currentOnClick()

                    val mousePosition = getMapPosition(
                        offset,
                        centerOfScreenState.value,
                        safeZoom,
                        userCoordinateState.value
                    )

                    val foundShape = currentShapes.lastOrNull { x ->
                        isInShape(mousePosition, x.position, x.size)
                    }

                    if (foundShape != null) {
                        if (actionState.value is Action.DoubleClicked) {
                            if ((actionState.value as Action.DoubleClicked).shape.id != foundShape.id) {
                                currentOnActionSet(Action.ShapeAction(foundShape))
                            }
                        } else {
                            currentOnActionSet(Action.ShapeAction(foundShape))
                        }
                    } else {
                        val foundConnection = findConnection(
                            minShapeSize = minShapeSize,
                            shapes = currentShapes,
                            connections = currentConnections,
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
                        }
                    }
                }
            },
            onDoubleClick = { it ->
                if (centerOfScreenState.value != Offset.Zero) {
                    val currentShapes = shapes.value
                    val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f

                    val mousePosition = getMapPosition(
                        it,
                        centerOfScreenState.value,
                        safeZoom,
                        userCoordinateState.value
                    )
                    val foundShape = currentShapes.lastOrNull { x ->
                        isInShape(mousePosition, x.position, x.size)
                    }

                    if (foundShape != null) {
                        currentOnActionSet(Action.DoubleClicked(foundShape))
                    } else {
                        currentOnActionSet(null)
                    }
                }
            },
            onCursorMove = { cursorOffset ->
                cursorPositionState.value = cursorOffset
            },
            onScrollChange = currentOnScrollChange,
            onGestureStart = { pointerChange ->
                if (centerOfScreenState.value != Offset.Zero) {
                    val currentShapes = shapes.value
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
                                pressure = 0.5f,
                                timestamp = 0L
                            )
                        )
                    } else {
                        val foundShapePair = getShapeGlobalDragType(
                            mousePosition = mousePosition,
                            action = actionState.value,
                            shapes = currentShapes,
                            sizeRound = currentSizeRound,
                            circleRadius = circleRadiusState.value
                        )

                        dragActionState.value = if (foundShapePair != null) {
                            val dragType = foundShapePair.first
                            val targetShape = foundShapePair.second
                            when (dragType) {
                                is DragType.Connection -> DragAction(
                                    startMapPosition = targetShape.position,
                                    accelerate = Offset(0f, 0f),
                                    dragType = DragType.Connection(
                                        startShapeId = targetShape.id,
                                        startShapeType = dragType.startShapeType,
                                        boxSide = targetShape
                                    )
                                )

                                is DragType.Resize -> DragAction(
                                    startMapPosition = mousePosition,
                                    accelerate = Offset(0f, 0f),
                                    dragType = dragType
                                )

                                is DragType.ShapeDrag -> DragAction(
                                    startMapPosition = targetShape.position,
                                    accelerate = targetShape.position,
                                    dragType = dragType
                                )
                            }
                        } else null
                    }
                }
            },
            onGesture = { _, gesturePan, gestureZoom, _, mainPointerInputChange, pointerList ->
                val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f

                val mousePosition = getMapPosition(
                    mainPointerInputChange.position,
                    centerOfScreenState.value,
                    safeZoom,
                    userCoordinateState.value
                )

                if (pointerList.size == 1) {
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
                        val off = (gesturePan / safeZoom)
                        val dragAction = dragActionState.value
                        if (dragAction != null) {
                            dragActionState.value = dragAction.copy(
                                accelerate = (dragAction.accelerate + off)
                            )
                        } else {
                            currentOnUserCoordinateChange(userCoordinateState.value - off)
                        }
                    }
                } else if (pointerList.size == 2) {
                    val newScale = safeZoom * gestureZoom
                    currentOnZoomChange(newScale)
                }
            },
            onGestureEnd = { pointerChange ->
                if (isDrawingState.value) {
                    currentOnDrawEnd()
                } else {
                    val currentShapes = shapes.value
                    val safeZoom = zoomState.value.takeIf { it != 0f } ?: 1f
                    val mousePosition = getMapPosition(
                        position = pointerChange.position,
                        centerOfScreen = centerOfScreenState.value,
                        zoom = safeZoom,
                        userCoordinate = userCoordinateState.value
                    )

                    val dragAction = dragActionState.value
                    if (dragAction != null) {
                        when (val action = dragAction.dragType) {
                            is DragType.Connection -> {
                                var foundSideShape: Pair<Shape<Id>, BoxSide>? = null
                                for (shape in currentShapes) {
                                    for (side in allSides) {
                                        val isInSide = isInSidePosition(
                                            mousePosition = mousePosition,
                                            itemPosition = shape.position,
                                            boxSize = shape.size,
                                            side = side,
                                            radius = currentSizeRound / 2f
                                        )
                                        if (isInSide) {
                                            foundSideShape = Pair(shape, side)
                                            break
                                        }
                                    }
                                }
                                if (foundSideShape != null) {
                                    currentOnAddConnection(
                                        AddShapeConnection(
                                            fromBoxId = action.startShapeId,
                                            toBoxId = foundSideShape.first.id,
                                            fromSide = action.startShapeType,
                                            toSide = foundSideShape.second,
                                        )
                                    )
                                }
                            }

                            is DragType.ShapeDrag -> {
                                val foundIndex =
                                    currentShapes.indexOfFirst { x -> x.id == action.shapeId }
                                if (foundIndex != -1) {
                                    currentOnMoveShape(foundIndex, dragAction.accelerate)
                                }
                            }

                            is DragType.Resize -> {
                                val resizeType = action.type
                                val foundIndex =
                                    currentShapes.indexOfFirst { x -> x.id == action.shapeId }

                                if (foundIndex != -1) {
                                    val shape = currentShapes[foundIndex]
                                    val shapePosition = shape.position
                                    val shapeSize = shape.size

                                    val roundToNearest = roundToNearestState.value
                                    val accelerate = dragAction.accelerate
                                    val resizePosition =
                                        resizePosition(accelerate, resizeType, roundToNearest)
                                    val shapeSizeApp = resizeSize(
                                        accelerate,
                                        resizeType,
                                        roundToNearest = roundToNearest
                                    )

                                    currentOnResizeShape(
                                        foundIndex,
                                        (shapePosition + resizePosition),
                                        (shapeSize + shapeSizeApp).keepCurrentOrMin(minShapeSize)
                                    )
                                }
                            }
                        }
                        dragActionState.value = null
                    }
                }
            },
            onGestureCancel = {}
        )
    }
}