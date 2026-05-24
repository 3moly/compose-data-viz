package com.threemoly.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import com.moly3.dataviz.core.graph.engine.impl.ultra.UltraFastEngine
import com.moly3.dataviz.core.graph.model.Connection
import com.moly3.dataviz.core.graph.model.GroupHullDef
import com.moly3.dataviz.core.graph.model.GroupId
import com.moly3.dataviz.core.graph.model.GroupMembership
import com.moly3.dataviz.core.graph.model.GroupModel
import com.moly3.dataviz.func.darker
import com.moly3.dataviz.graph.features.atlas.AtlasTier
import com.moly3.dataviz.graph.ui.AtlasPainterLoader
import com.moly3.dataviz.graph.ui.Graph
import com.moly3.dataviz.graph.ui.TierSelection
import com.moly3.dataviz.graph.ui.rememberAtlasComposer
import com.moly3.dataviz.graph.ui.rememberMovementTracker
import com.moly3.dataviz.sample.resources.Res
import com.moly3.dataviz.sample.resources.cat
import com.threemoly.sample.base.graph.GraphState
import com.threemoly.sample.base.graph.ObsidianGraphData
import com.threemoly.sample.base.graph.ObsidianGraphNode
import com.threemoly.sample.base.io
import com.threemoly.sample.base.uikit.SettingsPanel
import com.threemoly.sample.base.uikit.icons.Scale
import com.threemoly.sample.base.uikit.icons.Share
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random

private val random = Random(124)

// Stable keys for atlas tile lookup
const val KEY_FOLDER = "icon_folder"
const val KEY_SHARE = "icon_share"
private const val KEY_CAT = "icon_cat"

@Composable
fun GraphSample(
    engine: UltraFastEngine<String, ObsidianGraphData>,
    state: MutableState<GraphState>,
    nodeCountState: MutableState<Float>
) {

    val s = state.value
    val scale: Painter = rememberVectorPainter(Scale)
    val share: Painter = rememberVectorPainter(Share)
    val catPainter = painterResource(Res.drawable.cat)

    val context = LocalPlatformContext.current
    val coilImageLoader = remember { ImageLoader(context) }
    val loader: AtlasPainterLoader<String, ObsidianGraphData> = { node ->
        null
//        val index = state.value.graphNodes.indexOfFirst { it.id == node.id }
//        if (index < 0 || index >= 100) null
//        else {
//            val req = ImageRequest.Builder(context)
//                .data("https://picsum.photos/id/$index/300/300")
//                .size(128)
//                .allowConversionToBitmap(true)
//                .build()
//            coilImageLoader.execute(req).image?.toBitmap()?.asImage()?.asPainter(context)
//        }
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val movement = rememberMovementTracker(idleMillis = 1000)
    LaunchedEffect(s.velocities) { if (s.velocities.isNotEmpty()) movement.trigger() }
    LaunchedEffect(s.zoom) { movement.trigger() }
    LaunchedEffect(s.graphUserPosition) { movement.trigger() }
    val handle = rememberAtlasComposer(
        nodes = state.value.graphNodes,
        tiers = listOf(
            AtlasTier(
                name = "hq",
                tileSizePx = 256,
                selection = TierSelection.TopByDistance(3),
                isCircular = false,
                freezeOnMove = true,   // <-- was false
            ),
            AtlasTier(
                name = "lq",
                tileSizePx = 48,
                selection = TierSelection.All,
                isCircular = false,
                freezeOnMove = false,  // LQ can keep updating; it's cheap
            )
        ),

        viewport = viewport,
        userPosition = s.graphUserPosition,
        zoom = s.zoom,
        coordinates = s.coordinates,
        loader = loader,
        loaderKey = state.value.graphNodes.size, // or any token that should invalidate
        staticIcons = persistentMapOf(
            KEY_FOLDER to scale,
            KEY_SHARE to share,
            KEY_CAT to catPainter
        ),
        staticIconKey = { id, data -> /* return KEY_FOLDER / null / etc */ null },
        isMoving = movement.isMoving
    )
// 1. Add a seed state to force group regeneration
    var groupSeed by remember { mutableIntStateOf(0) }

// Stable group identities — these never change on rename.
    val groupIds = remember {
        listOf(GroupId("collection"), GroupId("row"), GroupId("file"), GroupId("tag"))
    }
// Editable display names, keyed by stable GroupId. Renaming touches ONLY this.
    val groupNames = remember {
        mutableStateMapOf(
            GroupId("collection") to "Collection",
            GroupId("row") to "Row",
            GroupId("file") to "File",
            GroupId("tag") to "Tag",
        )
    }
    val groupColors = remember {
        mapOf(
            GroupId("collection") to Color.Black,
            GroupId("row") to Color.Magenta,
            GroupId("file") to Color.Blue,
            GroupId("tag") to Color.Cyan,
        )
    }
    val groupModel = remember(s.graphNodes, groupSeed, groupNames.toMap()) {
        val defs = groupIds.map { id ->
            GroupHullDef(
                id = id,
                name = groupNames[id] ?: id.raw,
                color = groupColors[id] ?: Color.Red,
            )
        }

        val memberships = s.graphNodes.mapNotNull { node ->
            // Seed BOTH the inclusion check AND the group pick from the node id +
            // groupSeed, so remounting doesn't reshuffle memberships.
            val rng = Random(node.id.hashCode() + groupSeed)
            if (rng.nextFloat() > 0.2f) {
                val randomGroupId = groupIds.random(rng)
                GroupMembership(nodeId = node.id, groupId = randomGroupId)
            } else null
        }
        GroupModel(defs = defs, memberships = memberships).also {
            println("groupModel built, hash=${it.hashCode()}, mems=${it.memberships.size}")
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.darker(0.5f))
            .onGloballyPositioned { viewport = it.size }
    ) {
        Graph(
            textStyle = TextStyle.Default.copy(color = Color.Magenta),
            engine = engine,
            atlasLayers = handle.atlasLayers,
            watchNodeId = null,
            getIconKey = handle::resolveIconKey,
            groupModel = groupModel,
            isImmediateReheatOnUpdate = false,
            settings = s.graphSettings,

//            textStyle = TextStyle.Default.copy(color = Color.Magenta),
//            engine = engine,
//            atlasLayers = handle.atlasLayers,
//            watchNodeId = watchNodeState.value,
//            getIconKey = handle::resolveIconKey,
//            getNodeGroups = { _, data ->
//                when (data) {
//                    is ObsidianGraphData.Collection -> listOf("collection")
//                    is ObsidianGraphData.CollectionRow -> listOf("row")
//                    is ObsidianGraphData.File -> listOf("file")
//                    is ObsidianGraphData.Tag -> listOf("tag")
//                }
//            },
//            getGroupColor = { groupName ->
//                when (groupName) {
//                    "collection" -> Color.Black
//                    "row" -> Color.Magenta
//                    "file" -> Color.Blue
//                    "tag" -> Color.Cyan
//                    else -> Color.Red
//                }
//            },
//            isImmediateReheatOnUpdate = false,
//            customPopup = {
//                val cp =
//                    rememberAsyncImagePainter("https://composedataviz.3moly.com/images/cat4.jpg")
//                Image(
//                    painter = cp,
//                    contentDescription = null,
//                    modifier = Modifier.size(100.dp)
//                )
//            },
//            settings = s.graphSettings,
            consume = false,
            connections = s.connections,
            stateNodes = s.graphNodes,
            coordinates = s.coordinates,
            velocities = s.velocities,
            zoom = s.zoom,
            onZoomChange = { isGesture, newValue ->
                val newZoom = if (isGesture) {
                    newValue * state.value.zoom
                } else {
                    newValue * state.value.zoom * 0.05f + state.value.zoom
                }
                state.value = state.value.copy(zoom = newZoom)
//                state.value = if (isGesture) {
//                    state.value.copy(zoom = newValue * state.value.zoom)
//                } else {
//                    state.value.copy(zoom = newValue * state.value.zoom * 0.05f + state.value.zoom)
//                }
            },
            userPosition = s.graphUserPosition,
            onPanDelta = { delta ->
                val off = delta / state.value.zoom
                state.value =
                    state.value.copy(graphUserPosition = off + state.value.graphUserPosition)
            },
            onWatchPosition = { offset ->
                state.value = state.value.copy(graphUserPosition = offset)
            },
            onNodeClick = { node ->
                //watchNodeState.value = node.id
            },
            onCoordinatesUpdate = {
                state.value = state.value.copy(coordinates = it.toPersistentMap())
            },
            io = io,
        )

        // Debug preview: show both atlas bitmaps side-by-side
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.background(Color.White).padding(8.dp)) {
                Text(text = "Zoom: ${state.value.zoom}")
            }

            // Trigger random group assignments
            Button(
                onClick = { groupSeed++ },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text("Scramble Groups")
            }

            for (atlas in handle.atlasLayers.layers) {
                Image(
                    modifier = Modifier.padding(16.dp).size(100.dp),
                    bitmap = atlas.bitmap,
                    contentDescription = "layer 1"
                )
            }
        }
        // Trigger random group assignments
        Button(
            onClick = { groupSeed++ },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text("Scramble Groups")
        }

// Rename groups individually
        Column(
            modifier = Modifier
                .background(Color.White)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Rename groups:")
            for (id in groupIds) {
                var editing by remember(id) { mutableStateOf(false) }
                var draft by remember(id) { mutableStateOf(groupNames[id].orEmpty()) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(groupColors[id] ?: Color.Red)
                    )
                    Spacer(Modifier.size(8.dp))

                    if (editing) {
                        TextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val trimmed = draft.trim()
                            if (trimmed.isNotEmpty()) groupNames[id] = trimmed
                            editing = false
                        }) { Text("Save") }
                    } else {
                        Text(
                            text = groupNames[id].orEmpty(),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            draft = groupNames[id].orEmpty()
                            editing = true
                        }) { Text("Rename") }
                    }
                }
            }
        }
//        Box(Modifier.size(100.dp).background(if (movement.isMoving) Color.Magenta else Color.Green))

        // -------------- Settings panel --------------
        SettingsPanel(
            backgroundColor = Color.White,
            isShowSettings = s.isShowSettings,
            onSetSettings = {
                state.value = state.value.copy(isShowSettings = !state.value.isShowSettings)

            },
        ) {
            GraphSettingsContent(
                modifier = Modifier,
                settings = s.graphSettings,
                onChange = {
                    state.value = state.value.copy(graphSettings = it)
                    engine.nudge()
                },
                zoom = s.zoom,
                nodeCount = s.graphNodes.size,
                onNodeCountChange = { nodeCountState.value = it.toFloat() }
            )
        }
    }
}

private fun MutableState<GraphState>.spawnConnectedNode(sourceId: String) {
    val current = value
    val newId = "node ${random.nextInt()}"
    val nextSize = current.graphNodes.size + 1

    val newNode = ObsidianGraphNode(
        newId,
        name = newId,
        data = ObsidianGraphData.File(""),
        colorValue = Color.Red.darker(1f - nextSize / 100f).value
    )

    val nodes = current.graphNodes.toMutableList().apply { add(newNode) }
    val connections = current.connections.toMutableMap().apply {
        put(newId, persistentListOf(Connection(target = sourceId)))
        val existing = this[sourceId] ?: persistentListOf()
        put(sourceId, (existing + Connection(target = newId)).toPersistentList())
    }

    value = current.copy(
        graphNodes = nodes.toPersistentList(),
        connections = connections.toPersistentMap(),
    )
}