package com.moly3.dataviz.core.graph.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.jvm.JvmInline

/**
 * Stable identity for a group/hull. A value class so it's free at runtime
 * but type-safe — you can't accidentally pass a node Id where a group Id goes.
 */
@JvmInline
value class GroupId(
    val raw: String,
)

/**
 * Definition of a single group hull: identity + appearance only.
 * Carries NO membership and NO geometry — those are separate concerns.
 *
 * Because this is a plain immutable data class, a name or color change
 * is detected by structural equality. No signature hashing, no lambda
 * round-tripping.
 */
@Immutable
data class GroupHullDef(
    val id: GroupId,
    val name: String,
    val color: Color,
)

/**
 * One node's affiliation with one group, plus how strongly it belongs.
 *
 * [weight] is the "how much" — drives cohesion pull and hull inclusion.
 * 1.0 = full member; values < 1 = loose/partial member. A node may appear
 * in several memberships (multi-group), each with its own weight.
 */
@Immutable
data class GroupMembership<Id>(
    val nodeId: Id,
    val groupId: GroupId,
    val weight: Float = 1f,
)

/**
 * The complete, self-contained group model handed to the Graph composable.
 *
 * This single object replaces getNodeGroups / getGroupName / getGroupColor.
 * It is immutable and compares structurally, so the UI can key effects on
 * it directly — `LaunchedEffect(groupModel) { ... }` fires exactly when
 * something genuinely changed, and never otherwise.
 */
@Immutable
data class GroupModel<Id>(
    val defs: List<GroupHullDef> = emptyList(),
    val memberships: List<GroupMembership<Id>> = emptyList(),
) {
    companion object {
        fun <Id> empty(): GroupModel<Id> = GroupModel()
    }
}
