package com.threemoly.sample

import com.threemoly.sample.block.CanvasSample
import com.threemoly.sample.graph.GraphSample
import com.threemoly.sample.uikit.SettingsFuture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExampleApp() {
    MaterialTheme {
        var selected by remember { mutableStateOf("canvas") }

        Scaffold { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Left navigation rail
                NavigationRail(
                    header = {
                        Text(
                            "Samples",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                ) {
                    NavigationRailItem(
                        selected = selected == "canvas",
                        onClick = { selected = "canvas" },
                        icon = {
                            Icon(SettingsFuture, contentDescription = "Canvas Sample")
                        },
                        label = { Text("Canvas") }
                    )

                    NavigationRailItem(
                        selected = selected == "graph",
                        onClick = { selected = "graph" },
                        icon = {
                            Icon(
                                SettingsFuture,
                                contentDescription = "Graph Sample"
                            )
                        },
                        label = { Text("Graph") }
                    )
                }

                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (selected) {
                        "canvas" -> CanvasSample()
                        "graph" -> GraphSample()
                    }
                }
            }
        }
    }
}