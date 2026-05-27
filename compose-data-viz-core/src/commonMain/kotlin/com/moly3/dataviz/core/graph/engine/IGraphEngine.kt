package com.moly3.dataviz.core.graph.engine

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.hull.GroupSettings
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.dataviz.core.graph.model.GroupId
import com.moly3.dataviz.core.graph.model.GroupIndex

@Stable
interface IGraphEngine<Id, Data> {
    val isAsleep: Boolean
    suspend fun step(
        nodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        draggedNode: DragNodeData<Id>?,
        isMoving: Boolean,
        moveConnectedWhenPaused: Boolean = true,
    )

    fun reheat()
    fun nudge()

    /**
     * Supply the group model directly. Pass null to disable grouping.
     * The engine reads memberships (and their per-node weights) on the next step().
     */
    fun setGroupData(
        groupIndex: GroupIndex<Id>?,
        settings: GroupSettings,
        groupIndexIdentity: Int,
        suppressReheat: Boolean = false,
    )

    fun snapshotGroupsForHulls(): List<Pair<GroupId, FloatArray>>

    fun hasSyncedGroupIndex(groupIndexIdentity: Int): Boolean
}