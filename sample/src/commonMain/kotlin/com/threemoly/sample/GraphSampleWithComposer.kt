package com.threemoly.sample

import com.moly3.dataviz.graph.ui.TierSelection
import com.moly3.dataviz.graph.ui.rememberAtlasComposer
import kotlinx.collections.immutable.toPersistentMap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.moly3.dataviz.graph.features.atlas.AtlasTier
import com.moly3.dataviz.graph.ui.Graph
import com.threemoly.sample.base.graph.GraphState
import com.threemoly.sample.base.graph.ObsidianGraphData
import com.threemoly.sample.base.io
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun GraphSampleWithComposer(state: MutableState<GraphState>) {
    val s = state.value
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val folder = rememberVectorPainter(Icons.Default.Folder)
    val tag = rememberVectorPainter(Icons.Default.Tag)
    val staticIcons = remember(folder, tag) {
        persistentMapOf("folder" to folder, "tag" to tag)
    }

    // Heuristic: Are physics active?
    val isGraphMoving = remember(s.velocities) {
        s.velocities.values.any { it.getDistance() > 0.5f }
    }

    val atlasHandle = rememberAtlasComposer(
        nodes = s.graphNodes,
        isMoving = isGraphMoving,
        tiers = listOf(
            // High Quality: Only top 20, freeze during movement to save CPU/GPU
            AtlasTier("hq", tileSizePx = 128, selection = TierSelection.TopByDistance(20), freezeOnMove = true),
            // Low Quality: Broad fallback, updates while moving (throttled to 10fps by our LaunchedEffect)
            AtlasTier(
                "lq",
                tileSizePx = 32,
                selection = TierSelection.AllVisible,
                freezeOnMove = false
            )
        ),
        staticIcons = staticIcons,
        staticIconKey = { id, data ->
            // Route logic: Return a key for static, return NULL to trigger the async Composable builder
            when (data) {
                is ObsidianGraphData.Tag, is ObsidianGraphData.Collection -> "folder"
                is ObsidianGraphData.Tag -> "tag"
                is ObsidianGraphData.File -> "" // Tells the composer: "I need a dynamic canvas for this"
                else -> null
            }
        },
        viewport = viewport,
        userPosition = s.graphUserPosition,
        zoom = s.zoom,
        coordinates = s.coordinates,
        content = { node ->
            when (val d = node.data) {
                is ObsidianGraphData.File -> {
                    val url = d.fullPath
                    val painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalPlatformContext.current)
                            .data(url)
                            .crossfade(false)
                            .build()
                    )
                    val coilState by painter.state.collectAsState()

                    LaunchedEffect(coilState) {
                        setReady(coilState is AsyncImagePainter.State.Success)
                    }

                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(100.dp))
                    )
                }
                else -> { }
            }
        }
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .onGloballyPositioned { viewport = it.size }
    ) {
        atlasHandle.MountCaptureHolders()

        Graph(
            atlasLayers = atlasHandle.atlasLayers,
            getIconKey = { id, data -> atlasHandle.resolveIconKey(id, data) },
            stateNodes = s.graphNodes,
            connections = s.connections,
            coordinates = s.coordinates,
            velocities = s.velocities,
            zoom = s.zoom,
            onZoomChange = { state.value = state.value.copy(zoom = it) },
            userPosition = s.graphUserPosition,
            onCentralGlobalPosition = { state.value = state.value.copy(graphUserPosition = it) },
            onCoordinatesUpdate = {
                state.value = state.value.copy(coordinates = it.toPersistentMap())
            },
            onNodeClick = { },
            settings = s.graphSettings,
            consume = false,
            io = io,
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            atlasHandle.atlasLayers.layers.forEachIndexed { i, layer ->
                Text("Layer $i (${layer.bitmap.width}×${layer.bitmap.height}, ${layer.indexMap.size} tiles)")
                Image(
                    bitmap = layer.bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
            }
        }
    }
}