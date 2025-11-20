package com.threemoly.sample.block

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.rememberAsyncImagePainter
import com.mikepenz.hypnoticcanvas.shaderBackground
import com.moly3.dataviz.block.func.absoluteOffset
import com.moly3.dataviz.block.ui.Canvas
import com.moly3.dataviz.core.block.model.Action
import com.moly3.dataviz.core.block.model.ShapeConnection
import com.moly3.dataviz.core.block.model.CanvasSettings
import com.moly3.dataviz.core.block.model.StylusPath
import com.moly3.dataviz.func.darker
import com.threemoly.sample.shader.UmlShader
import com.threemoly.sample.uikit.BButton
import com.threemoly.sample.uikit.ObsText
import com.threemoly.sample.uikit.SettingsPanel
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
    "https://i.redd.it/dqm6bph5mqxb1.jpg"

@OptIn(ExperimentalTime::class)
@Composable
fun CanvasSample(
    shapes: SnapshotStateList<CustomShape>,
    connections: SnapshotStateList<ShapeConnection>,
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
    val actionState = remember { mutableStateOf<Action?>(value = null) }

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
            sideCircleColor = Color.Blue,
            selectedShapeBorderColor = Color.Blue
        )
    }

    fun changePicture(shape: CustomShape, newPictureUrl: String) {
        val index = shapes.indexOf(shape)
        shapes[index] = shapes[index].copy(data = ShapeData.ImageUrl(newPictureUrl))
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState, zIndex = 0f)
                .shaderBackground(shader = selectedShader)
        )
        Canvas(
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
            onAddConnection = { connection ->
                connections.add(connection)
            },
            isDrawing = isDrawingState.value,
            onDrawBlock = { shapeState ->
                val borderCoef by animateFloatAsState(
                    if (shapeState.isSelected) 3f else 1f
                )
                val bgColor = if (shapeState.isDoubleClicked) {
                    Color.Yellow.copy(alpha = 0.2f)
                } else {
                    Color.Black.copy(alpha = 0.3f) // Dark semi-transparent
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
                        .hazeSource(hazeState, zIndex = 2f + shapeState.shape.id)
                        .hazeEffect(hazeState, hazeStyle) // Apply blur first
                        .background(bgColor) // Then semi-transparent overlay
                        .border((1f * zoomState.value * borderCoef).dp, Color.White)
                ) {
                    when (val data = shapeState.shape.data) {
                        is ShapeData.ImageUrl -> {
                            if (shapeState.isDoubleClicked) {
                                val isSelected = remember { mutableStateOf(false) }
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
                            ObsText(
                                modifier = Modifier.align(Alignment.Center),
                                text = data.text,
                                color = Color.White
                            )
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
                                    centerWidth = it.size.width.toFloat() / actualDensity.density
                                }
                        ) {
                            Box(Modifier.size(50.dp).background(Color.Red).clickable {
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
            },
            drawingPaths = paths,
            onActionSet = {
                actionState.value = it
            },
            onAddPath = { path ->
                paths.add(path.copy(color = Color.Cyan))
            }
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
            Box(Modifier.size(30.dp).background(Color.Gray).clickable {
                shapes.add(
                    CustomShape(
                        Clock.System.now().toEpochMilliseconds(),
                        position = Offset(0f, 0f),
                        size = Offset(50f, 50f),
                        color = Color.Red,
                        data = ShapeData.Text("change me")
                    )
                )
            })
            Box(
                Modifier.size(30.dp)
                    .background(if (isDrawingState.value) Color.Green else Color.Gray).clickable {
                        isDrawingState.value = !isDrawingState.value
                    })
        }
    }
}