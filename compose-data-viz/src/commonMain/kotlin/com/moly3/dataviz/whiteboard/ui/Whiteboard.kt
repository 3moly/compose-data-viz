package com.moly3.dataviz.whiteboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.moly3.dataviz.core.whiteboard.model.CanvasSettings
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.DrawShapeState
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.StylusPath
import com.moly3.dataviz.core.whiteboard.model.StylusPoint
import kotlin.math.abs

@Composable
fun <ShapeType : Shape<Id>, Id> Whiteboard(
    consume: Boolean,
    modifier: Modifier,
    action: Action<ShapeType, Id>?,
    backgroundModifier: Modifier,
    connectionsModifier: Modifier,
    settings: CanvasSettings,
    zoom: Float,
    roundToNearest: Int?,
    connectionDragBlankId: Id,
    userCoordinate: Offset,
    circleRadius: Float?,
    isDrawing: Boolean,
    shapes: List<ShapeType>,
    connections: List<ShapeConnection<Id>>,
    drawingPaths: List<StylusPath>,
    onActionSet: (Action<ShapeType, Id>?) -> Unit,
    onAddPath: (StylusPath) -> Unit,
    onMoveShape: (Int, Offset) -> Unit,
    onResizeShape: (Int, Offset, Offset) -> Unit,
    onAddConnection: (AddShapeConnection<Id>) -> Unit,
    onZoomChange: (Float) -> Unit,
    onUserCoordinateChange: (Offset) -> Unit,
    settingsPanel: @Composable (position: Offset, action: Action<ShapeType, Id>, onDoneAction: () -> Unit) -> Unit,
    onDrawBlock: @Composable (DrawShapeState<ShapeType, Id>) -> Unit,
) {
    var currentPath by remember { mutableStateOf<List<StylusPoint>>(listOf()) }
    val scaleMovementModifier = 5f
    val sizeRound = 25
    val actualDensity = LocalDensity.current

    val scope = rememberCoroutineScope()

    val strokeWidth = remember { Animatable(1f) }

    val isHomeHoldState = remember { mutableStateOf(false) }
    val cursorPositionState = remember { mutableStateOf(Offset(0.0F, 0.0F)) }
    val centerOfScreenState = remember { mutableStateOf(Offset(0.0F, 0.0F)) }


    val centerOfScreen = remember(centerOfScreenState.value) { centerOfScreenState.value }

    val cursorPosition = remember(cursorPositionState.value) { cursorPositionState.value }

    LaunchedEffect(action) {
        strokeWidth.snapTo(1f)
        if (action is Action.Connection) {
            strokeWidth.animateTo(4f)
        }
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

    val mapCursor =
        remember(cursorPosition, centerOfScreen, zoom, userCoordinate) {
            getMapPosition(
                cursorPosition,
                centerOfScreen,
                zoom,
                userCoordinate
            )
        }
    val dragActionState = remember { mutableStateOf<DragAction<Id>?>(null) }

    val pointer =
        remember(
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
            circleRadius
        ) {
            calculatePointer(
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
                action = action
            )
        }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(0.dp))
    ) {
        Box(backgroundModifier.fillMaxSize())
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                DrawConnections(
                    modifier = connectionsModifier,
                    paths = drawingPaths,
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
                    mousePosition = mapCursor,
                    shapes = shapes,
                    dragActionState = dragActionState,
                    userCoordinate = userCoordinate,
                    zoom = zoom,
                    density = actualDensity.density,
                    action = action,
                    onDrawBlock = onDrawBlock,
                    sideCircleColor = settings.sideCircleColor,
                    roundToNearest = roundToNearest
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
                            centerOfScreenState.value =
                                Offset(
                                    it.size.width.toFloat(),
                                    it.size.height.toFloat()
                                ) / 2f
                        }
                        .dashboard(
                            scope = scope,
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

                            onScrollChange = {
                                if (isHomeHoldState.value && it.y != 0f) {
                                    onZoomChange(abs(zoomState.value + it.y / 100f))
                                } else {
                                    val userCoordinate = userCoordinateState.value
                                    onUserCoordinateChange(userCoordinate - it * scaleMovementModifier)
                                }
                            },
                            onDrawStart = { point ->
                                val strokeWidth = (point.pressure * 15f).coerceIn(2f, 20f)
                                val pointWithStroke = point.copy(strokeWidth = strokeWidth)
                                currentPath = (currentPath + pointWithStroke)
                            },
                            onDrawChange = { point ->
                                val strokeWidth = (point.pressure * 15f).coerceIn(2f, 20f)
                                val pointWithStroke = point.copy(strokeWidth = strokeWidth)
                                currentPath =
                                    (currentPath.toMutableList() + pointWithStroke)
                            },
                            onDrawEnd = {
                                if (currentPath.isNotEmpty()) {
                                    onAddPath(
                                        StylusPath(
                                            points = currentPath.toList(),
                                            color = Color.Red
                                        )
                                    )
                                    currentPath = listOf()
                                }
                            },
                            dragActionState = dragActionState,
                            onClick = { },
                            onMoveShape = onMoveShape,
                            onResizeShape = onResizeShape,
                            onAddConnection = onAddConnection,
                            onZoomChange = onZoomChange,
                            onUserCoordinateChange = onUserCoordinateChange
                        )
                        .pointerHoverIcon(pointer.pointerIcon)
                ) {}
            }
        }
        Box(Modifier.fillMaxSize()) {
            val center: Offset? =
                remember(
                    action,
                    zoom,
                    userCoordinate,
                    centerOfScreen,
                    shapes.map { x -> x.position },
                    dragActionState.value,
                    roundToNearest
                ) {
                    if (action == null)
                        null
                    else {
                        val dragAction = dragActionState.value
                        val addOffset = if (dragAction != null) {
                            when (dragAction.dragType) {
                                is DragType.Connection -> Offset.Zero
                                is DragType.Resize -> {
                                    when (action) {
                                        is Action.Connection -> Offset.Zero
                                        is Action.DoubleClicked -> Offset.Zero
                                        is Action.ShapeAction -> {
                                            if (action.shape.id == (dragAction.dragType as DragType.Resize).shapeId) {
                                                dragAction.accelerate.copy(y = 0f) / 2f
                                            } else {
                                                Offset.Zero
                                            }
                                        }
                                    }
                                }

                                is DragType.ShapeDrag -> {
                                    when (action) {
                                        is Action.Connection -> Offset.Zero
                                        is Action.DoubleClicked -> Offset.Zero
                                        is Action.ShapeAction -> {
                                            if (action.shape.id == (dragAction.dragType as DragType.ShapeDrag).shapeId) {
                                                dragAction.accelerate - dragAction.startMapPosition
                                            } else {
                                                Offset.Zero
                                            }
                                        }
                                    }

                                }
                            }
                        } else Offset.Zero
                        (action.getOffsetMenu<ShapeType>(shapes = shapes) - userCoordinate + addOffset.roundToNearest(
                            roundToNearest
                        )) * zoom + centerOfScreen
                    }
                }
            if (center != null && action != null) {
                settingsPanel(center, action, {
                    onActionSet(null)
                })
            }
        }
    }
}