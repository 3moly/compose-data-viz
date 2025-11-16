package com.threemoly.sample.block

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.mikepenz.hypnoticcanvas.shaderBackground
import com.moly3.dataviz.block.func.absoluteOffset
import com.moly3.dataviz.block.model.Action
import com.moly3.dataviz.block.model.ArcConnection
import com.moly3.dataviz.block.model.CanvasSettings
import com.moly3.dataviz.block.model.Shape
import com.moly3.dataviz.block.model.StylusPath
import com.moly3.dataviz.block.model.WorldPosition
import com.moly3.dataviz.block.ui.Canvas
import com.threemoly.sample.shader.UmlShader
import com.moly3.dataviz.func.darker
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.collections.listOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class MyShape(
    override val id: Long,
    val color: Color?,
    override val position: WorldPosition,
    override val size: Offset
) : Shape(id, position, size)

@OptIn(ExperimentalTime::class)
@Composable
fun CanvasSample() {
//    val hazeStyle = remember(back) {
//        HazeStyle(
//            backgroundColor = backgroundSecondary,
//            tints = listOf(HazeTint(back.copy(0.1f))),
//            blurRadius = 4.dp,
//            noiseFactor = HazeDefaults.noiseFactor
//        )
//    }
    val shapes = remember<SnapshotStateList<MyShape>> {
        mutableStateListOf()
    }
    val connections = remember<SnapshotStateList<ArcConnection>> {
        mutableStateListOf()
    }
    val paths = remember<SnapshotStateList<StylusPath>> {
        mutableStateListOf()
    }
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
    val zoomState = remember { mutableStateOf(1f) }
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
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState, zIndex = 0f)
                .shaderBackground(shader = selectedShader)
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize(),
            backgroundModifier = Modifier,
            connectionsModifier = Modifier.hazeSource(hazeState, zIndex = 1f),
            settings = CanvasSettings(
                defaultLineColor = Color.Cyan,
                sideCircleColor = Color.Blue
            ),
            zoomState = zoomState,
            userCoordinateState = userCoordinateState,

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
                    Modifier
                        .fillMaxSize()
                        .hazeEffect(hazeState, hazeStyle) // Apply blur first
                        .background(bgColor) // Then semi-transparent overlay
                        .border((1f * zoomState.value * borderCoef).dp, Color.White)
                )
            },
//            onDrawBlock = { shapeState ->
//                val colorCoef by animateColorAsState(if (shapeState.isDoubleClicked) Color.Yellow else Color.Gray)
//                val borderCoef by animateFloatAsState(if (shapeState.isSelected) 3f else 1f)
//                Box(
//                    Modifier
//                        .fillMaxSize()
//                        .hazeEffect(hazeState, hazeStyle)
////                        .background(colorCoef)
//                        .border((1f * zoomState.value * borderCoef).dp, Color.White)
//                )
//            },
            settingsPanel = { offset, action, onDoneAction ->
                var centerWidth by remember { mutableStateOf(0f) }
                val actualDensity = LocalDensity.current
                when (action) {
                    is Action.DoubleClicked -> {}

                    is Action.Connection,
                    is Action.Shape -> {
                        Row(
                            Modifier
                                .absoluteOffset(
                                    (offset / LocalDensity.current.density - Offset(
                                        centerWidth / 2f,
                                        50f
                                    ) - Offset(0f, 8f))
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
                                    is Action.Shape -> {
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
            onAddPath = { path ->
                paths.add(path.copy(color = Color.Cyan))
            }
        )
        Column(modifier = Modifier.padding(32.dp).align(Alignment.TopEnd).width(70.dp)) {
            Slider(
                modifier = Modifier,
                value = zoomState.value,
                valueRange = 0.1f..5f,
                onValueChange = {
                    zoomState.value = it
                })
        }
        Row(
            modifier = Modifier.padding(bottom = 46.dp).align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(30.dp).background(Color.Gray).clickable {
                shapes.add(
                    MyShape(
                        Clock.System.now().toEpochMilliseconds(),
                        position = WorldPosition(0f, 0f),
                        size = Offset(50f, 50f),
                        color = Color.Red
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