package com.moly3.dataviz.core.graph.engine.impl.hybrid

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.hull.GroupSettings
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.dataviz.core.graph.model.GroupId
import com.moly3.dataviz.core.graph.model.GroupIndex

class HybridGraphRenderer<Id, Data> : IGraphEngine<Id, Data> {
    private val calculator = HybridForceCalculator<Id, Data>()
    val totalEnergy: Float get() = lastEnergy
    override val isAsleep = false
    private var lastEnergy = 0f
    private val energyHolder = FloatArray(1)

    override suspend fun step(
        nodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        draggedNode: DragNodeData<Id>?,
        isMoving: Boolean
    ) {
        applyHybridForces(
            nodes, connections, settings, coordinates, velocities,
            draggedNode, calculator, energyHolder
        )
        lastEnergy = energyHolder[0]
    }

    override fun reheat() {

    }

    override fun nudge() {

    }

    override fun setGroupData(
        groupIndex: GroupIndex<Id>?,
        settings: GroupSettings,
        groupIndexIdentity: Int,
        suppressReheat: Boolean
    ) {
        TODO("Not yet implemented")
    }


    override fun snapshotGroupsForHulls(): List<Pair<GroupId, FloatArray>> {
        TODO("Not yet implemented")
    }

    override fun hasSyncedGroupIndex(groupIndexIdentity: Int): Boolean {
        TODO("Not yet implemented")
    }

    /** True when the simulation has settled - caller can skip frames to save battery. */
    fun isSettled(): Boolean = lastEnergy < 0.5f
}