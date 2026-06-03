![Maven Central Version](https://img.shields.io/maven-central/v/io.github.3moly/compose-data-viz)

# compose-data-visualization

Two canvases for Compose Multiplatform — a whiteboard, and a node graph.

<p align="middle">
    <img src="docs/media/whiteboard_demo.jpg" width="49%" />
    <img src="./docs/media/graph_demo.png" width="49%" />
</p>

<sub>Android &nbsp; WASM &nbsp; JS &nbsp; iOS &nbsp; JVM &nbsp; macOS &nbsp; · &nbsp; <a href="https://composedataviz.3moly.com/">Live demo</a></sub>

```kotlin
implementation("io.github.3moly:compose-data-viz:0.1.0")
implementation("io.github.3moly:compose-data-viz-core:0.1.0") // models only, optional
```

<br>

---

<br>

## Graph

<sub>A force-directed node graph, rendered on the GPU. Built for large, living datasets.</sub>

<br>

<table>
<tr>
<td width="38%" valign="top">
<sub>01</sub><br>
<b>Movement</b>
<p><sub>A frame-paced engine. The same motion on a phone and a desktop. It rests when settled, and stirs again on change.</sub></p>
</td>
<td width="62%">
<!-- docs/media/graph/movement.gif -->
<img src="docs/media/graph/movement.gif" width="100%" />
</td>
</tr>

<tr><td colspan="2"><br></td></tr>

<tr>
<td width="38%" valign="top">
<sub>02</sub><br>
<b>Atlas</b>
<p><sub>Node icons are packed into a single texture and drawn together. Near nodes are kept sharp; distant ones step back.</sub></p>
</td>
<td width="62%">
<!-- docs/media/graph/atlas.png -->
<img src="docs/media/graph/atlas.png" width="100%" />
</td>
</tr>

<tr><td colspan="2"><br></td></tr>

<tr>
<td width="38%" valign="top">
<sub>03</sub><br>
<b>Groups</b>
<p><sub>Related nodes are enclosed by a soft hull, filled and titled. The outline follows them as they drift.</sub></p>
</td>
<td width="62%">
<!-- docs/media/graph/groups.png -->
<img src="docs/media/graph/groups.png" width="100%" />
</td>
</tr>

<tr><td colspan="2"><br></td></tr>

<tr>
<td width="38%" valign="top">
<sub>04</sub><br>
<b>Labels</b>
<p><sub>Only the nearest names are shown. They scale with the view and fade at distance. The chosen node carries a quiet pill.</sub></p>
</td>
<td width="62%">
<!-- docs/media/graph/labels.png -->
<img src="docs/media/graph/labels.png" width="100%" />
</td>
</tr>

<tr><td colspan="2"><br></td></tr>

<tr>
<td width="38%" valign="top">
<sub>05</sub><br>
<b>Edges</b>
<p><sub>Lines carry direction, dashes, and weight. They thin out as you zoom away. A plain mode draws the very large graph.</sub></p>
</td>
<td width="62%">
<!-- docs/media/graph/edges.png -->
<img src="docs/media/graph/edges.png" width="100%" />
</td>
</tr>

<tr><td colspan="2"><br></td></tr>

<tr>
<td width="38%" valign="top">
<sub>06</sub><br>
<b>Focus</b>
<p><sub>Choose a node and the rest recedes. Or follow one, and the view keeps it at the center as the graph moves.</sub></p>
</td>
<td width="62%">
<!-- docs/media/graph/focus.gif -->
<img src="docs/media/graph/focus.gif" width="100%" />
</td>
</tr>

<tr><td colspan="2"><br></td></tr>

<tr>
<td width="38%" valign="top">
<sub>07</sub><br>
<b>Touch</b>
<p><sub>Pan, pinch, and drag. A moved node disturbs only its neighbours; the rest holds still.</sub></p>
</td>
<td width="62%">
<!-- docs/media/graph/touch.gif -->
<img src="docs/media/graph/touch.gif" width="100%" />
</td>
</tr>
</table>

<br>

<details>
<summary><sub><code>Graph()</code></sub></summary>

```kotlin
@Composable
fun <Id, Data> Graph(
    modifier: Modifier = Modifier,
    settings: GraphSettings = GraphSettings.Default,
    consume: Boolean,
    userPosition: Offset,
    zoom: Float,
    stateNodes: List<GraphNode<Id, Data>>,
    coordinates: Map<Id, Offset>,
    velocities: Map<Id, Offset>,
    connections: Map<Id, List<Connection<Id>>>,
    atlasLayers: AtlasLayers = AtlasLayers.EMPTY,
    getIconKey: (Id, Data) -> String? = { _, _ -> null },
    groupModel: GroupModel<Id> = GroupModel.empty(),
    watchNodeId: Id? = null,
    io: CoroutineContext,
    onPanDelta: (Offset) -> Unit,
    onZoomChange: (Boolean, Float) -> Unit,
    onNodeClick: (GraphNode<Id, Data>) -> Unit,
    onCoordinatesUpdate: (Map<Id, Offset>) -> Unit = {},
    customPopup: (@Composable (node: GraphNode<Id, Data>) -> Unit)? = null,
)
```

Styling is grouped in `GraphSettings` — `view`, `text`, `edge`, `selection`, `watch`, `groupSettings`, `theme`.

</details>

<br>

---

<br>

## Whiteboard

<sub>A Miro-style infinite canvas. Everything is a shape — an id, a position, a size. You draw it.</sub>

<br>

```kotlin
interface Shape<Id> {
    val id: Id
    val position: Offset
    val size: Offset
}
```

<details>
<summary><sub><code>Whiteboard()</code></sub></summary>

```kotlin
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
)
```

</details>

<br>

---

<br>

<sub>Used in &nbsp; · &nbsp; <a href="https://github.com/3moly/CedarJam">CedarJam</a></sub>