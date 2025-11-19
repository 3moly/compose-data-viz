package com.moly3.dataviz.block.func

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.moly3.dataviz.block.minShapeSize
import com.moly3.dataviz.core.block.model.Action
import com.moly3.dataviz.core.block.model.ArcConnection
import com.moly3.dataviz.core.block.model.BoxSide
import com.moly3.dataviz.core.block.model.ConnectionConfig
import com.moly3.dataviz.core.block.model.DragAction
import com.moly3.dataviz.core.block.model.DragType
import com.moly3.dataviz.core.block.model.Shape
import com.moly3.dataviz.core.block.model.ResizeType
import com.moly3.dataviz.core.block.model.StylusPoint
import com.moly3.dataviz.core.block.model.allSides
import androidx.compose.ui.geometry.Offset
import com.moly3.gesture.PointerRequisite
import com.moly3.gesture.detectPointerTransformGestures
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
fun Modifier.dashboard(
    scope: CoroutineScope,
    sizeRound: Int,
    roundToNearest: State<Int?>,
    zoomState: State<Float>,
    userCoordinateState: State<Offset>,
    isDrawingState: State<Boolean>,

    cursorPositionState: MutableState<Offset>,
    centerOfScreenState: MutableState<Offset>,

    connectionConfig: ConnectionConfig,

    shapes: State<List<Shape>>,
    connections: State<List<ArcConnection>>,
    dragActionState: MutableState<DragAction?>,
    actionState: State<Action?>,

    onZoomChange: (Float) -> Unit,
    onUserCoordinateChange: (Offset) -> Unit,

    onScrollChange: (delta: Offset) -> Unit = {},

    onActionSet: (Action?) -> Unit,
    onAddConnection: (ArcConnection) -> Unit,
    onMoveShape: (Int, Offset) -> Unit,
    onResizeShape: (Int, Offset, Offset) -> Unit,

    onDrawStart: (StylusPoint) -> Unit,
    onDrawChange: (StylusPoint) -> Unit,
    onDrawEnd: () -> Unit,
    onClick: () -> Unit,
): Modifier {
    return this.pointerInput(Unit) {
        detectPointerTransformGestures(
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
                    shapes.firstOrNull { x ->
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
                        config = connectionConfig
                    )
                    if (foundConnection != null) {
                        onActionSet(Action.Connection(foundConnection))
                    }
                }
            },
            onDoubleClick = {
                val shapes = shapes.value
                val connections = connections.value
                val mousePosition = getMapPosition(
                    it,
                    centerOfScreenState.value,
                    zoomState.value,
                    userCoordinateState.value
                )
                val foundShape =
                    shapes.firstOrNull { x ->
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
                    val foundShape = shapes.firstNotNullOfOrNull { x ->
                        val inShape = isInShapeComplex(
                            mousePosition,
                            x.position,
                            x.size,
                            circleRadius = 12f
                        )
                        if (inShape != null) {
                            Pair(x, inShape)
                        } else
                            null
                    }
                    if (foundShape != null) {
                        val shape = foundShape.first
                        when (val sh = foundShape.second) {
                            InShape.Body -> {
                                dragActionState.value = DragAction(
                                    startMapPosition = shape.position,
                                    accelerate = shape.position,
                                    dragType = DragType.ShapeDrag(
                                        shapeId = shape.id
                                    )
                                )
                            }

                            is InShape.Resize -> {
                                dragActionState.value = DragAction(
                                    startMapPosition = mousePosition,
                                    accelerate = Offset(0f, 0f),
                                    dragType = DragType.Resize(
                                        shapeId = shape.id,
                                        type = sh.resizeType,
                                        boxSide = shape
                                    )
                                )
                            }
                        }
                    } else {
                        var foundResize: Pair<Shape, ResizeType>? = null
                        for (shape in shapes) {
                            val resizeType = isInResizeArea(
                                mousePosition = mousePosition,
                                shapePosition = shape.position,
                                shapeSize = shape.size,
                                detectionPercent = 0.1f
                            )
                            if (resizeType != null) {
                                foundResize = Pair(shape, resizeType)
                            }
                        }
                        var foundSideShape: Pair<Shape, BoxSide>? = null
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
                            val foundShape = foundSideShape.first
                            dragActionState.value = DragAction(
                                startMapPosition = foundShape.position,
                                accelerate = Offset(0f, 0f),
                                dragType = DragType.Connection(
                                    startShapeId = foundShape.id,
                                    startShapeType = foundSideShape.second,
                                    boxSide = foundShape
                                )
                            )
                        } else if (foundResize != null) {
                            val foundShape = foundResize.first
                            dragActionState.value = DragAction(
                                startMapPosition = mousePosition,
                                accelerate = Offset(0f, 0f),
                                dragType = DragType.Resize(
                                    shapeId = foundShape.id,
                                    type = foundResize.second,
                                    boxSide = foundShape
                                )
                            )
                        } else if (foundShape != null) {

                        }
                    }
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
                                var foundSideShape: Pair<Shape, BoxSide>? = null
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
                                        ArcConnection(
                                            id = Clock.System.now()
                                                .toEpochMilliseconds(),
                                            fromBox = action.startShapeId,
                                            toBox = foundSideShape.first.id,
                                            fromSide = action.startShapeType,
                                            toSide = foundSideShape.second,
                                            arcHeight = 80f,
                                            color = null
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

                                    val accelerate = dragAction.accelerate
                                    val resizePosition =
                                        resizePosition(accelerate, resizeType)
                                    val shapeSizeApp =
                                        resizeSize(
                                            accelerate,
                                            resizeType,
                                            roundToNearest = roundToNearest.value
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
                        //todo isDrawingState.value = false
                    }
                }
            },
            onGestureCancel = {}
        )
    }
}