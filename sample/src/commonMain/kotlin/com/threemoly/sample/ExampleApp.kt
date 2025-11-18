package com.threemoly.sample

import com.threemoly.sample.block.CanvasSample
import com.threemoly.sample.graph.GraphSample
import com.threemoly.sample.uikit.SettingsFuture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

const val canvasPage = "Canvas"
const val graphPage = "Graph"

data class Page(
    val key: String,
    val text: String,
    val icon: ImageVector
)

@Composable
fun ExampleApp() {
    MaterialTheme {
        val pages =
            remember {
                listOf(
                    Page(
                        key = canvasPage,
                        text = "Canvas",
                        icon = SettingsFuture
                    ),
                    Page(
                        key = graphPage,
                        text = "Graph",
                        icon = SettingsFuture
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
                                Text(
                                    "Samples",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        ) {
                            for (page in pages) {
                                NavigationItem(page, selectedPage) { selectedPage = it }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedPage.key) {
                            canvasPage -> CanvasSample()
                            graphPage -> GraphSample()
                        }
                    }
                }
            }
        }
    }
}