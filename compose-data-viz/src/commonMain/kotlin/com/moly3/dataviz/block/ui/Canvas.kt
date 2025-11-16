package com.moly3.dataviz.block.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.block.func.calculatePointer
import com.moly3.dataviz.block.func.dashboard
import com.moly3.dataviz.block.func.getMapPosition
import com.moly3.dataviz.block.model.Action
import com.moly3.dataviz.block.model.ArcConnection
import com.moly3.dataviz.block.model.CanvasSettings
import com.moly3.dataviz.block.model.ConnectionConfig
import com.moly3.dataviz.block.model.DragAction
import com.moly3.dataviz.block.model.DrawShapeState
import com.moly3.dataviz.block.model.Shape
import com.moly3.dataviz.block.model.StylusPath
import com.moly3.dataviz.block.model.StylusPoint
import com.moly3.dataviz.block.model.WorldPosition
import kotlin.math.abs

@Composable
fun Canvas(
    modifier: Modifier,
    backgroundModifier: Modifier,
    connectionsModifier: Modifier,
    settings: CanvasSettings,
    zoomState: MutableState<Float>,
    userCoordinateState: MutableState<Offset>,
    isDrawing: Boolean,
    shapes: List<Shape>,
    connections: List<ArcConnection>,
    drawingPaths: List<StylusPath>,
    onAddPath: (StylusPath) -> Unit,
    onMoveShape: (Int, WorldPosition) -> Unit,
    onResizeShape: (Int, WorldPosition, Offset) -> Unit,
    onAddConnection: (ArcConnection) -> Unit,
    settingsPanel: @Composable (position: Offset, action: Action, onDoneAction: () -> Unit) -> Unit,
    onDrawBlock: @Composable (DrawShapeState) -> Unit,
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
    val actionState = remember { mutableStateOf<Action?>(value = null) }

    val centerOfScreen = remember(centerOfScreenState.value) { centerOfScreenState.value }

    val cursorPosition = remember(cursorPositionState.value) { cursorPositionState.value }
    val action = remember(actionState.value) { actionState.value }

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
        remember(cursorPosition, centerOfScreen, zoomState.value, userCoordinateState.value) {
            getMapPosition(
                cursorPosition,
                centerOfScreen,
                zoomState.value,
                userCoordinateState.value
            )
        }
    val dragActionState = remember { mutableStateOf<DragAction?>(null) }

    val pointer =
        remember(
            mapCursor,
            shapes,
            connections,
            zoomState.value,
            centerOfScreen,
            connectionConfig,
            userCoordinateState.value,
            cursorPosition
        ) {
            calculatePointer(
                shapes = shapes,
                mapCursor = mapCursor,
                connections = connections,
                zoom = zoomState.value,
                connectionConfig = connectionConfig,
                userCoordinate = userCoordinateState.value,
                centerOfScreen = centerOfScreen,
                cursorPosition = cursorPosition,
                dragAction = dragActionState.value,
                sizeRound = sizeRound
            )
        }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent(onKeyEvent = { event ->
                if (Key.MetaLeft == event.key) {
                    isHomeHoldState.value = event.type == KeyEventType.KeyDown
                } else if (
                    event.key == Key.Backspace ||
                    event.key == Key.Delete
                ) {
                    //todo tryDeleteAction()
                }
                true
            })
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(0.dp))
    ) {
        Box(backgroundModifier.fillMaxSize())
        Row(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                DrawConnections(
                    modifier=connectionsModifier,
                    paths = drawingPaths,
                    stylusPoint = currentPath,
                    shapes = shapes,
                    connections = connections,
                    dragActionState = dragActionState,
                    userCoordinate = userCoordinateState.value,
                    config = connectionConfig,
                    zoom = zoomState.value,
                    centerOfScreen = centerOfScreen,
                    cursorPosition = cursorPosition,
                    action = action,
                    selectedConnectionStrokeWidth = strokeWidth.value,
                    lineColor = settings.defaultLineColor,
                    drawColor = Color.White
                )
                DrawShapes(
                    mousePosition = mapCursor,
                    shapes = shapes,
                    dragActionState = dragActionState,
                    userCoordinate = userCoordinateState.value,
                    zoom = zoomState.value,
                    density = actualDensity.density,
                    action = action,
                    onDrawBlock = onDrawBlock,
                    sideCircleColor = settings.sideCircleColor,
                )
                val updatedShapes = rememberUpdatedState(shapes)
                val updatedConnections = rememberUpdatedState(connections)
                val isDrawingState = rememberUpdatedState(isDrawing)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            centerOfScreenState.value =
                                Offset(it.size.width.toFloat(), it.size.height.toFloat()) / 2f
                        }
                        .dashboard(
                            scope = scope,

                            zoomState = zoomState,
                            sizeRound = sizeRound,
                            cursorPositionState = cursorPositionState,
                            centerOfScreenState = centerOfScreenState,
                            userCoordinateState = userCoordinateState,
                            connectionConfig = connectionConfig,
                            isDrawingState = isDrawingState,
                            actionState = actionState,

                            shapes = updatedShapes,
                            connections = updatedConnections,

                            horizontalChange = {
                                userCoordinateState.value =
                                    userCoordinateState.value.copy(x = userCoordinateState.value.x - it * scaleMovementModifier)
                            },
                            verticalChange = {
                                if (isHomeHoldState.value) {
                                    zoomState.value = abs(zoomState.value + it / 100f)
                                } else {
                                    userCoordinateState.value =
                                        userCoordinateState.value.copy(y = userCoordinateState.value.y - it * scaleMovementModifier)
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
                            onClick = { focusRequester.requestFocus() },
                            onMoveShape = onMoveShape,
                            onResizeShape = onResizeShape,
                            onAddConnection = onAddConnection
                        )
                        .pointerHoverIcon(pointer.pointerIcon)
                ) {}
            }
        }
        Box(Modifier.fillMaxSize()) {
            val center: Offset? =
                remember(action, zoomState.value, userCoordinateState.value, centerOfScreen) {
                    if (action == null)
                        null
                    else {
                        (action.getOffsetMenu() - userCoordinateState.value) * zoomState.value + centerOfScreen
                    }
                }
            if (center != null && action != null) {
                settingsPanel(center, action, {
                    actionState.value = null
                })
            }
        }
    }
}