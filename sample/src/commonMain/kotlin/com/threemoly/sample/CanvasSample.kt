package com.threemoly.sample

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.rememberAsyncImagePainter
import com.mikepenz.hypnoticcanvas.shaderBackground
import com.moly3.dataviz.whiteboard.func.absoluteOffset
import com.moly3.dataviz.whiteboard.ui.Whiteboard
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.ShapeConnection
import com.moly3.dataviz.core.whiteboard.model.CanvasSettings
import com.moly3.dataviz.core.whiteboard.model.StylusPath
import com.moly3.dataviz.func.darker
import com.threemoly.sample.base.block.CustomShape
import com.threemoly.sample.base.block.ShapeData
import com.threemoly.sample.base.uikit.shader.UmlShader
import com.threemoly.sample.base.uikit.BButton
import com.threemoly.sample.base.uikit.ButtonIcon
import com.threemoly.sample.base.uikit.ObsText
import com.threemoly.sample.base.uikit.SettingsPanel
import com.threemoly.sample.base.uikit.icons.DownCircle
import com.threemoly.sample.base.uikit.icons.Edit1
import com.threemoly.sample.base.uikit.icons.Plus
import com.threemoly.sample.base.uikit.icons.TrashCan
import com.threemoly.sample.base.uikit.icons.UpCircle
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

const val catUrl =
    "https://png.pngtree.com/png-clipart/20230511/ourmid/pngtree-isolated-cat-on-white-background-png-image_7094927.png"
const val dogUrl =
    "https://t3.ftcdn.net/jpg/08/28/49/30/360_F_828493018_Ntaia2HBMK7UHVFyP8jv0UrTcD7Fk7pw.jpg"
const val sharkUrl =
    "https://www.freeiconspng.com/uploads/animal-shark-png-4.png"

const val anotherCatUrl =
    "https://composedataviz.3moly.com/images/cat4.jpg"

@OptIn(ExperimentalTime::class)
@Composable
fun CanvasSample(
    shapes: SnapshotStateList<CustomShape>,
    connections: SnapshotStateList<ShapeConnection<Long>>,
    paths: SnapshotStateList<StylusPath>
) {

    val isDrawingState = remember { mutableStateOf(false) }
    val backgroundSecondary = Color.Black.darker(0.8f)
    val hazeState = rememberHazeState(blurEnabled = true)
    val hazeStyle = remember(backgroundSecondary) {
        HazeStyle(
            backgroundColor = backgroundSecondary,
            tints = listOf(HazeTint(backgroundSecondary.copy(0.1f))),
            blurRadius = 4.dp,
            noiseFactor = HazeDefaults.noiseFactor
        )
    }
    val actionState = remember { mutableStateOf<Action<CustomShape, Long>?>(value = null) }

    val isShowSettings = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val zoomState = remember(density) { mutableStateOf(density.density) }
    val strokeWidthState = remember { mutableStateOf(1f) }
    val roundToNearest = remember { mutableStateOf(0) }
    val userCoordinateState = remember { mutableStateOf(Offset(0f, 0f)) }
    val selectedShader: UmlShader by remember { mutableStateOf(UmlShader) }
    LaunchedEffect(backgroundSecondary) {
        selectedShader.setColor(backgroundSecondary)
    }
    LaunchedEffect(userCoordinateState.value, zoomState.value) {
        selectedShader.userCoordinates = userCoordinateState.value
        selectedShader.zoom = zoomState.value
        selectedShader.dotSpacing = 50f
    }
    val canvasSettings = remember(strokeWidthState.value) {
        CanvasSettings(
            strokeWidth = strokeWidthState.value,
            defaultLineColor = Color.Cyan,
            sideCircleColor = Color.Blue
        )
    }

    fun changePicture(shape: CustomShape, newPictureUrl: String) {
        val index = shapes.indexOf(shape)
        shapes[index] = shapes[index].copy(data = ShapeData.ImageUrl(newPictureUrl))
    }

    fun changeText(shape: CustomShape, newText: String) {
        val index = shapes.indexOf(shape)
        shapes[index] = shapes[index].copy(data = ShapeData.Text(newText))
    }
    Row(Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState, zIndex = 0f)
                    .shaderBackground(shader = selectedShader)
            )
            Whiteboard(
                modifier = Modifier.fillMaxSize(),
                action = actionState.value,
                roundToNearest = roundToNearest.value,
                backgroundModifier = Modifier,
                connectionsModifier = Modifier.hazeSource(hazeState, zIndex = 1f),
                settings = canvasSettings,
                zoom = zoomState.value,
                onZoomChange = {
                    zoomState.value = it
                },
                userCoordinate = userCoordinateState.value,
                onUserCoordinateChange = {
                    userCoordinateState.value = it
                },
                shapes = shapes,
                connections = connections,
                onMoveShape = { index, position ->
                    shapes[index] = shapes[index].copy(position = position)
                },
                onResizeShape = { index, position, size ->
                    shapes[index] = shapes[index].copy(position = position, size = size)
                },
                onAddConnection = { addConnection ->
                    connections.add(
                        ShapeConnection(
                            id = Clock.System.now().toEpochMilliseconds(),
                            fromBoxId = addConnection.fromBoxId,
                            toBoxId = addConnection.toBoxId,
                            fromSide = addConnection.fromSide,
                            toSide = addConnection.toSide,
                            arcHeight = 80f,
                            color = null
                        )
                    )
                },
                isDrawing = isDrawingState.value,
                onDrawBlock = { shapeState ->
                    val borderCoef by animateFloatAsState(
                        if (shapeState.isSelected) 3f else 1f
                    )
                    val bgColor = if (shapeState.isDoubleClicked) {
                        Color.Yellow.copy(alpha = 0.2f)
                    } else {
                        (shapeState.shape.backgroundColor
                            ?: Color.Black).copy(alpha = 0.3f) // Dark semi-transparent
                    }
                    Box(
                        shapeState.modifier
                            .let {
                                if (shapeState.isDoubleClicked) {
                                    it.zIndex(100f)
                                } else
                                    it
                            }
                            .fillMaxSize()
                            .hazeSource(hazeState, zIndex = 2f + shapeState.index)
                            .hazeEffect(hazeState, hazeStyle) // Apply blur first
                            .background(bgColor) // Then semi-transparent overlay
                            .border((1f * zoomState.value * borderCoef).dp, Color.White)
                    ) {
                        when (val data = shapeState.shape.data) {
                            is ShapeData.ImageUrl -> {
                                if (shapeState.isDoubleClicked) {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        BButton(text = "cat", fontColor = Color.White) {
                                            changePicture(
                                                shapeState.shape,
                                                catUrl
                                            )
                                            actionState.value = null
                                        }
                                        BButton(text = "dog", fontColor = Color.White) {
                                            changePicture(
                                                shapeState.shape,
                                                dogUrl
                                            )
                                            actionState.value = null
                                        }
                                        BButton(text = "shark", fontColor = Color.White) {
                                            changePicture(
                                                shapeState.shape,
                                                sharkUrl
                                            )
                                            actionState.value = null
                                        }
                                        BButton(text = "cat2", fontColor = Color.White) {
                                            changePicture(
                                                shapeState.shape,
                                                anotherCatUrl
                                            )
                                            actionState.value = null
                                        }
                                    }
                                } else {
                                    Image(
                                        modifier = Modifier.fillMaxSize(),
                                        painter = rememberAsyncImagePainter(data.url),
                                        contentDescription = null
                                    )
                                }

                            }

                            is ShapeData.Text -> {
                                if (shapeState.isDoubleClicked) {
                                    val textState =
                                        remember {
                                            mutableStateOf(
                                                TextFieldValue(
                                                    data.text,
                                                    selection = TextRange(data.text.length)
                                                )
                                            )
                                        }
                                    val focusRequest = remember { FocusRequester() }
                                    OutlinedTextField(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .fillMaxWidth()
                                            .focusRequester(focusRequest),
                                        value = textState.value,
                                        singleLine = true,
                                        onValueChange = {
                                            textState.value = it
                                        },
                                        keyboardActions = KeyboardActions {
                                            changeText(
                                                shapeState.shape,
                                                newText = textState.value.text
                                            )
                                            actionState.value = null
                                        },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.White)
                                    )
                                    LaunchedEffect(Unit) {
                                        focusRequest.requestFocus()
                                    }
                                } else {
                                    ObsText(
                                        modifier = Modifier.align(Alignment.Center),
                                        text = data.text,
                                        color = Color.White,
                                        fontSize = (12 * zoomState.value / density.density).sp
                                    )
                                }
                            }
                        }
                    }
                },
                settingsPanel = { offset, action, onDoneAction ->
                    var centerWidth by remember { mutableStateOf(0f) }
                    val actualDensity = LocalDensity.current
                    when (action) {
                        is Action.DoubleClicked -> {}

                        is Action.Connection,
                        is Action.ShapeAction -> {
                            Row(
                                Modifier
                                    .absoluteOffset(
                                        ((offset / LocalDensity.current.density - Offset(
                                            centerWidth / 2f,
                                            50f
                                        ) - Offset(0f, 8f)))
                                    )
                                    .onGloballyPositioned {
                                        centerWidth =
                                            it.size.width.toFloat() / actualDensity.density
                                    }
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ButtonIcon(
                                        modifier = Modifier,
                                        color = Color.White,
                                        painter = rememberVectorPainter(DownCircle),
                                        onClick = {
                                            when (action) {
                                                is Action.Connection -> {

                                                }

                                                is Action.DoubleClicked -> TODO()
                                                is Action.ShapeAction -> {
                                                    val foundOld =
                                                        shapes.indexOfFirst { d -> d.id == action.shape.id }
                                                    if (foundOld >= 0) {
                                                        val old = shapes[foundOld]
                                                        shapes.removeAt(foundOld)
                                                        shapes.add(0, old)
                                                    }
                                                }
                                            }
                                            onDoneAction()
                                        })
                                    ButtonIcon(
                                        modifier = Modifier,
                                        color = Color.White,
                                        painter = rememberVectorPainter(UpCircle),
                                        onClick = {
                                            when (action) {
                                                is Action.Connection -> {

                                                }

                                                is Action.DoubleClicked -> TODO()
                                                is Action.ShapeAction -> {
                                                    val foundOld =
                                                        shapes.indexOfFirst { d -> d.id == action.shape.id }
                                                    if (foundOld >= 0) {
                                                        val old = shapes[foundOld]
                                                        shapes.removeAt(foundOld)
                                                        shapes.add(old)
                                                    }
                                                }
                                            }
                                            onDoneAction()
                                        })
                                    ButtonIcon(
                                        modifier = Modifier,
                                        color = Color.White,
                                        painter = rememberVectorPainter(TrashCan),
                                        onClick = {
                                            when (action) {
                                                is Action.Connection -> {
                                                    connections.remove(action.selectedConnection.connection)
                                                }

                                                is Action.DoubleClicked -> TODO()
                                                is Action.ShapeAction -> {
                                                    shapes.remove(action.shape)
                                                }
                                            }
                                            onDoneAction()
                                        })
                                }
                            }
                        }
                    }
                },
                drawingPaths = paths,
                onActionSet = {
                    actionState.value = it
                },
                consume = false,
                onAddPath = { path ->
                    paths.add(path.copy(color = Color.Cyan))
                },
                connectionDragBlankId = 1L,
                circleRadius = 12f
            )
            SettingsPanel(
                backgroundColor = Color.White,
                isShowSettings = isShowSettings.value,
                onSetSettings = {
                    isShowSettings.value = !isShowSettings.value
                }) {
                Slider(
                    modifier = Modifier,
                    value = zoomState.value,
                    valueRange = 0.1f..5f,
                    onValueChange = {
                        zoomState.value = it
                    })
                Text(text = "round to nearest (${roundToNearest.value}):")
                Slider(
                    modifier = Modifier,
                    value = roundToNearest.value.toFloat(),
                    valueRange = 0f..100f,
                    onValueChange = {
                        roundToNearest.value = it.toInt()
                    })
                Text(text = "stroke width (${strokeWidthState.value}):")
                Slider(
                    modifier = Modifier,
                    value = strokeWidthState.value,
                    valueRange = 0.5f..20f,
                    onValueChange = {
                        strokeWidthState.value = it
                    })
            }
            Row(
                modifier = Modifier.padding(bottom = 46.dp).align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ButtonIcon(modifier = Modifier, painter = rememberVectorPainter(Plus), onClick = {
                    shapes.add(
                        CustomShape(
                            Clock.System.now().toEpochMilliseconds(),
                            position = userCoordinateState.value,
                            size = Offset(100f, 50f),
                            backgroundColor = Color.Gray,
                            data = ShapeData.Text("change me")
                        )
                    )
                })
                ButtonIcon(
                    modifier = Modifier,
                    color = animateColorAsState(if (isDrawingState.value) Color.Green else Color.White).value,
                    painter = rememberVectorPainter(Edit1), onClick = {
                        isDrawingState.value = !isDrawingState.value
                    })
            }
        }
//        LazyColumn(Modifier.width(200.dp).fillMaxHeight()) {
//            items(
//                items = shapes,
//                key = { it.id } // or any unique identifier
//            ) { shape ->
//                Column(
//                    Modifier.fillMaxWidth(),
//                    verticalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    ObsText(text = "Shap #" + shape.id.toString())
//                    ObsText(text = "size: ${shape.size.x.toInt()}:${shape.size.y.toInt()}")
//                    ObsText(text = "position: ${shape.position.x.toInt()}:${shape.position.y.toInt()}")
//                    ObsText(text = "data: ${shape.data}")
//                }
//                // your item composable
//            }
//        }
    }
}