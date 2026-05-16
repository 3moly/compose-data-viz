package com.moly3.dataviz.core.graph.engine

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.hull.GroupSettings
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings

@Stable
interface IGraphEngine<Id, Data> {
    val isAsleep: Boolean
    suspend fun step(
        nodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        draggedNode: DragNodeData<Id>?
    )

    fun reheat()
    fun nudge()
    fun setGroupData(
        groupsForNodeIndex: ((Int) -> List<String>)?,
        settings: GroupSettings,
    )

    fun snapshotGroupsForHulls(): List<Pair<String, FloatArray>>
}