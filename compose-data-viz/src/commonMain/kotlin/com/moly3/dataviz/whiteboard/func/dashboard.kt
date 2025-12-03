package com.moly3.dataviz.whiteboard.func

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.moly3.dataviz.whiteboard.minShapeSize
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.ShapeConnection
import com.moly3.dataviz.core.whiteboard.model.BoxSide
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.StylusPoint
import com.moly3.dataviz.core.whiteboard.model.allSides
import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.whiteboard.model.AddShapeConnection
import com.moly3.gesture.PointerRequisite
import com.moly3.gesture.detectPointerTransformGestures
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
fun <ShapeType : Shape<Id>, Id> Modifier.dashboard(
    scope: CoroutineScope,
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
): Modifier {
    return this.pointerInput(Unit) {
        detectPointerTransformGestures(
            consume = consume,
            scope = scope,
            numberOfPointers = 0,
            requisite = PointerRequisite.GreaterThan,
            onClick = { offset ->
                val shapes = shapes.value
                val connections = connections.value
                println("onClick: shapes: ${shapes.size}")

                onClick()

                val mousePosition = getMapPosition(
                    offset,
                    centerOfScreenState.value,
                    zoomState.value,
                    userCoordinateState.value
                )

                val foundShape =
                    shapes.lastOrNull { x ->
                        isInShape(
                            mousePosition,
                            x.position,
                            x.size
                        )
                    }
                if (foundShape != null) {
                    if (actionState.value is Action.DoubleClicked) {
                        if ((actionState.value as Action.DoubleClicked).shape.id == foundShape.id) {

                        } else {
                            onActionSet(Action.ShapeAction(foundShape))
                        }
                    } else {
                        onActionSet(Action.ShapeAction(foundShape))
                    }

                } else {
                    val foundConnection = findConnection(
                        shapes = shapes,
                        connections = connections,
                        dragAction = dragActionState.value,
                        cursorPosition = cursorPositionState.value,
                        centerOfScreen = centerOfScreenState.value,
                        userCoordinate = userCoordinateState.value,
                        zoom = zoomState.value,
                        config = connectionConfig,
                        roundToNearest = roundToNearestState.value
                    )
                    if (foundConnection != null) {
                        onActionSet(Action.Connection(foundConnection))
                    }
                }
            },
            onDoubleClick = {
                val shapes = shapes.value
                val mousePosition = getMapPosition(
                    it,
                    centerOfScreenState.value,
                    zoomState.value,
                    userCoordinateState.value
                )
                val foundShape =
                    shapes.lastOrNull { x ->
                        isInShape(
                            mousePosition,
                            x.position,
                            x.size
                        )
                    }
                if (foundShape != null) {
                    onActionSet(Action.DoubleClicked(foundShape))
                } else {
                    onActionSet(null)
                }
            },
            onCursorMove = { cursorOffset ->
                cursorPositionState.value = cursorOffset
            },
            onScrollChange = onScrollChange,
            onGestureStart = { pointerChange ->
                val shapes = shapes.value
                val mousePosition = getMapPosition(
                    pointerChange.position,
                    centerOfScreenState.value,
                    zoomState.value,
                    userCoordinateState.value
                )

                if (isDrawingState.value) {
                    onDrawStart(
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
                        shapes = shapes,
                        sizeRound = sizeRound,
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

                            is DragType.ShapeDrag -> {
                                DragAction(
                                    startMapPosition = targetShape.position,
                                    accelerate = targetShape.position,
                                    dragType = dragType
                                )
                            }
                        }
                    } else
                        null
                }
            },
            onGesture = { gestureCentroid: Offset,
                          gesturePan: Offset,
                          gestureZoom: Float,
                          gestureRotate: Float,
                          mainPointerInputChange: PointerInputChange,
                          pointerList: List<PointerInputChange> ->
                val mousePosition = getMapPosition(
                    mainPointerInputChange.position,
                    centerOfScreenState.value,
                    zoomState.value,
                    userCoordinateState.value
                )

                if (pointerList.size == 1) {
                    if (isDrawingState.value) {
                        val pressure = extractPressure(mainPointerInputChange)
                        val tilt = extractTilt(mainPointerInputChange)
                        val strokeWidth = (pressure * 15f).coerceIn(2f, 20f)
                        onDrawChange(
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
                        val off = (gesturePan / zoomState.value)
                        val dragAction = dragActionState.value
                        if (dragAction != null) {
                            dragActionState.value = dragAction.copy(
                                accelerate = ((dragAction.accelerate + off))
                            )
                        } else {
                            onUserCoordinateChange(userCoordinateState.value - off)
                        }
                    }
                } else if (pointerList.size == 2) {
                    val newScale = zoomState.value * gestureZoom
                    onZoomChange(newScale)
                }
            },
            onGestureEnd = { pointerChange ->
                if (isDrawingState.value) {
                    onDrawEnd()
                } else {
                    val shapes = shapes.value
                    val mousePosition = getMapPosition(
                        position = pointerChange.position,
                        centerOfScreen = centerOfScreenState.value,
                        zoom = zoomState.value,
                        userCoordinate = userCoordinateState.value
                    )
                    val dragAction = dragActionState.value
                    if (dragAction != null) {
                        when (val action = dragAction.dragType) {
                            is DragType.Connection -> {
                                var foundSideShape: Pair<Shape<Id>, BoxSide>? = null
                                for (shape in shapes) {
                                    for (side in allSides) {
                                        val isInSide = isInSidePosition(
                                            mousePosition = mousePosition,
                                            itemPosition = shape.position,
                                            boxSize = shape.size,
                                            side = side,
                                            radius = sizeRound / 2f
                                        )
                                        if (isInSide) {
                                            foundSideShape = Pair(shape, side)
                                            break
                                        }
                                    }
                                }
                                if (foundSideShape != null) {
                                    onAddConnection(
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
                                    shapes.indexOfFirst { x -> x.id == action.shapeId }
                                if (foundIndex != -1) {
                                    onMoveShape(foundIndex, dragAction.accelerate)
                                }
                            }

                            is DragType.Resize -> {
                                val resizeType = action.type
                                val foundIndex =
                                    shapes.indexOfFirst { x -> x.id == action.shapeId }

                                if (foundIndex != -1) {
                                    val shape = shapes[foundIndex]
                                    val shapePosition = shape.position
                                    val shapeSize = shape.size

                                    val roundToNearest = roundToNearestState.value
                                    val accelerate = dragAction.accelerate
                                    val resizePosition =
                                        resizePosition(accelerate, resizeType, roundToNearest)
                                    val shapeSizeApp =
                                        resizeSize(
                                            accelerate,
                                            resizeType,
                                            roundToNearest = roundToNearest
                                        )

                                    onResizeShape(
                                        foundIndex,
                                        (shapePosition + resizePosition),
                                        (shapeSize + shapeSizeApp).keepCurrentOrMin(
                                            minShapeSize
                                        )
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