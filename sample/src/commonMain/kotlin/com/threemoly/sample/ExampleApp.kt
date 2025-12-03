package com.threemoly.sample

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.whiteboard.model.ShapeConnection
import com.moly3.dataviz.core.whiteboard.model.BoxSide
import com.moly3.dataviz.core.whiteboard.model.StylusPath
import com.threemoly.sample.base.Page
import com.threemoly.sample.base.uikit.BottomNavigationBar
import com.threemoly.sample.base.uikit.NavigationItem
import com.threemoly.sample.base.block.CustomShape
import com.threemoly.sample.base.block.ShapeData
import com.threemoly.sample.base.func.generateRandomGraphState
import com.threemoly.sample.base.func.openUrl
import com.threemoly.sample.base.io
import com.threemoly.sample.base.uikit.icons.GithubSvgrepoCom
import com.threemoly.sample.base.uikit.icons.Scale
import com.threemoly.sample.base.uikit.icons.Share
import kotlinx.coroutines.launch

const val canvasPage = "Canvas"
const val graphPage = "Graph"

@Composable
fun ExampleApp() {
    val nodeCountState = remember { mutableStateOf(15f) }
    val density = LocalDensity.current
    val graphState = remember(density) {
        mutableStateOf(
            generateRandomGraphState(
                nodeCount = nodeCountState.value.toInt(),
                connectionsPercentPerNode = 10f
            ).copy(zoom = density.density)
        )
    }
    LaunchedEffect(nodeCountState.value) {
        launch(io) {
            val newState = generateRandomGraphState(
                nodeCount = nodeCountState.value.toInt(),
                connectionsPercentPerNode = 10f
            )
            graphState.value = graphState.value.copy(
                graphNodes = newState.graphNodes,
                connections = newState.connections
            )
        }
    }

    val shapes = remember {
        mutableStateListOf(
            CustomShape(
                id = 1L,
                backgroundColor = Color.Red,
                position = Offset(-200f, -100f),
                size = Offset(250f, 100f),
                data = ShapeData.Text("click, drag, resize")
            ),
            CustomShape(
                id = 2L,
                backgroundColor = Color.Cyan,
                position = Offset(150f, -200f),
                size = Offset(150f, 250f),
                data = ShapeData.ImageUrl(catUrl)
            ),
            CustomShape(
                id = 3L,
                backgroundColor = Color.Black,
                position = Offset(0f, 100f),
                size = Offset(250f, 70f),
                data = ShapeData.Text("double click on the picture")
            ),
        )
    }
    val connections = remember {
        mutableStateListOf(
            ShapeConnection(
                id = 0L,
                fromSide = BoxSide.RIGHT,
                toSide = BoxSide.LEFT,
                fromBoxId = 1L,
                toBoxId = 2L,
                color = Color.Magenta
            ),
            ShapeConnection(
                id = 1L,
                fromSide = BoxSide.TOP,
                toSide = BoxSide.BOTTOM,
                fromBoxId = 2L,
                toBoxId = 1L,
                color = Color.Green
            ),
            ShapeConnection(
                id = 2L,
                fromSide = BoxSide.LEFT,
                toSide = BoxSide.RIGHT,
                fromBoxId = 3L,
                toBoxId = 2L,
                color = Color.Yellow
            )
        )
    }
    val paths = remember<SnapshotStateList<StylusPath>> {
        mutableStateListOf()
    }
    MaterialTheme {
        val pages =
            remember {
                listOf(
                    Page(
                        key = canvasPage,
                        text = "Canvas",
                        icon = Scale
                    ),
                    Page(
                        key = graphPage,
                        text = "Graph",
                        icon = Share
                    )
                )
            }
        var selectedPage by remember(pages) { mutableStateOf(pages.first()) }

        BoxWithConstraints {
            val widthDp = maxWidth
            val isMobile = widthDp < 600.dp

            Scaffold(
                bottomBar = {
                    if (isMobile) {
                        BottomNavigationBar(pages, selectedPage, onSelect = { selectedPage = it })
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (!isMobile) {
                        NavigationRail(
                            header = {
                                Column {
                                    Text(
                                        "3moly/\ncompose-data-diz\nSamples",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        ) {
                            for (page in pages) {
                                NavigationItem(page, selectedPage) { selectedPage = it }
                            }
                            Box(Modifier.weight(1f))
                            NavigationRailItem(
                                selected = false,
                                onClick = {
                                    openUrl("https://github.com/3moly/compose-data-viz")
                                },
                                icon = {
                                    Icon(
                                       GithubSvgrepoCom,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                label = { Text("Github") }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedPage.key) {
                            canvasPage -> CanvasSample(
                                shapes = shapes,
                                connections = connections,
                                paths = paths
                            )

                            graphPage -> GraphSample(
                                state = graphState,
                                nodeCountState = nodeCountState
                            )
                        }
                    }
                }
            }
        }
    }
}