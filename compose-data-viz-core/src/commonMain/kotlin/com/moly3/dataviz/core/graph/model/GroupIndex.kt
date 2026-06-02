package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable

/**
 * Precomputed bidirectional lookups over a [GroupModel]. Build once when the
 * model changes; both the engine's syncGroupsInternal and the hull controller
 * read from this instead of invoking resolver lambdas.
 */
@Immutable
class GroupIndex<Id> private constructor(
    val model: GroupModel<Id>,
    private val byNode: Map<Id, List<GroupMembership<Id>>>,
    private val byGroup: Map<GroupId, List<GroupMembership<Id>>>,
    private val defById: Map<GroupId, GroupHullDef>,
    val identity: Int,
) {
    /**
     * Stable identity of the membership topology this index represents.
     * Two GroupIndex instances built from equal GroupModels share this value.
     * Used by the engine to tag published snapshots so cross-coroutine readers
     * can confirm a snapshot was built from the index they expect.
     */

    val groupIds: List<GroupId> get() = model.defs.map { it.id }

    fun membershipsOf(nodeId: Id): List<GroupMembership<Id>> = byNode[nodeId] ?: emptyList()

    fun membersOf(groupId: GroupId): List<GroupMembership<Id>> = byGroup[groupId] ?: emptyList()

    fun defOf(groupId: GroupId): GroupHullDef? = defById[groupId]

    /** Group ids a node belongs to — replaces the old getNodeGroups lambda. */
    fun groupsForNode(nodeId: Id): List<GroupId> = membershipsOf(nodeId).map { it.groupId }

    companion object {
        fun <Id> build(model: GroupModel<Id>): GroupIndex<Id> {
            val byNode = model.memberships.groupBy { it.nodeId }
            val byGroup = model.memberships.groupBy { it.groupId }
            val defById = model.defs.associateBy { it.id }
            return GroupIndex(
                model,
                byNode,
                byGroup,
                defById,
                identity = model.hashCode(),
            )
        }
    }
}
