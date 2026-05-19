package com.moly3.dataviz.core.graph.engine.impl.ultra

import com.moly3.dataviz.core.graph.model.GroupId

/**
 * Immutable, fully-consistent snapshot of the engine's group state.
 *
 * Built ONCE at the end of [UltraFastEngine.syncGroupsInternal], after every
 * group SoA array has been completely written, then published atomically via
 * an AtomicReference.
 *
 * The hull controller reads ONLY from a published instance of this class —
 * never from the engine's live, mutable arrays. This is what removes the
 * cross-coroutine race: there is no longer any window in which a reader can
 * observe half-reallocated `nodeGroupId` / half-filled `nodeGroupOffset` /
 * a stale `groupIdToValue`. Either you see the whole old snapshot or the
 * whole new one.
 *
 * All arrays here are private copies sized EXACTLY to the live data — they are
 * never the engine's over-allocated capacity buffers, so they cannot change
 * underneath a reader.
 *
 * @property groupIds       dense engine-index -> GroupId (size == groupCount)
 * @property nodeGroupOffset CSR partition, size == nodeCount + 1
 * @property nodeGroupId     CSR values: engine group index per membership
 * @property nodeGroupWeight CSR values: membership weight, parallel to nodeGroupId
 */
internal class GroupSnapshot private constructor(
    val groupCount: Int,
    val nodeCount: Int,
    val groupIds: Array<GroupId>,
    val nodeGroupOffset: IntArray,
    val nodeGroupId: IntArray,
    val nodeGroupWeight: FloatArray,
) {
    /**
     * Build the (groupId, [x0,y0,x1,y1,...]) hull point lists from this
     * snapshot's membership topology plus the supplied per-node positions.
     *
     * [posX] / [posY] are the engine's live position arrays. They are read
     * here purely for geometry; a one-frame skew between this snapshot's
     * topology and the very latest positions is harmless (positions are
     * continuous — a node never teleports between groups), and it self-heals
     * on the next poll. The thing that MUST be consistent is the topology
     * itself, and that is fully captured by this immutable snapshot.
     */
    fun buildHullPoints(posX: FloatArray, posY: FloatArray): List<Pair<GroupId, FloatArray>> {
        if (groupCount == 0 || nodeCount == 0) return emptyList()

        // How many positions land in each group.
        val counts = IntArray(groupCount)
        for (i in 0 until nodeCount) {
            val from = nodeGroupOffset[i]
            val to = nodeGroupOffset[i + 1]
            for (k in from until to) {
                val gid = nodeGroupId[k]
                if (gid in 0 until groupCount) counts[gid]++
            }
        }

        val pts = Array(groupCount) { FloatArray(counts[it] * 2) }
        val wIdx = IntArray(groupCount)
        for (i in 0 until nodeCount) {
            val from = nodeGroupOffset[i]
            val to = nodeGroupOffset[i + 1]
            // Defensive: a position array shorter than nodeCount means the
            // engine reallocated between sync and read — skip rather than crash.
            if (i >= posX.size || i >= posY.size) break
            val px = posX[i]
            val py = posY[i]
            for (k in from until to) {
                val gid = nodeGroupId[k]
                if (gid !in 0 until groupCount) continue
                val w = wIdx[gid]
                pts[gid][w] = px
                pts[gid][w + 1] = py
                wIdx[gid] = w + 2
            }
        }

        val out = ArrayList<Pair<GroupId, FloatArray>>(groupCount)
        for (gi in 0 until groupCount) {
            if (counts[gi] > 0) out.add(groupIds[gi] to pts[gi])
        }
        return out
    }

    companion object {
        /** Published before the first sync — reads return empty, never crash. */
        val EMPTY = GroupSnapshot(
            groupCount = 0,
            nodeCount = 0,
            groupIds = emptyArray(),
            nodeGroupOffset = IntArray(1),
            nodeGroupId = IntArray(0),
            nodeGroupWeight = FloatArray(0),
        )

        /**
         * Construct a snapshot from the engine's live arrays. Every array is
         * COPIED to its exact used length so the snapshot is fully detached
         * from the engine's mutable, over-allocated capacity buffers.
         *
         * Must be called only at the end of syncGroupsInternal, when all of
         * `nodeGroupOffset[0..n]`, `nodeGroupId[0..w]`, `nodeGroupWeight[0..w]`
         * and `groupIdToValue` are fully written.
         */
        fun build(
            groupCount: Int,
            nodeCount: Int,
            groupIdToValue: Array<GroupId>,
            liveNodeGroupOffset: IntArray,
            liveNodeGroupId: IntArray,
            liveNodeGroupWeight: FloatArray,
            entryCount: Int,
        ): GroupSnapshot {
            if (groupCount == 0 || nodeCount == 0) return EMPTY

            val offset = liveNodeGroupOffset.copyOf(nodeCount + 1)
            val ids = liveNodeGroupId.copyOf(entryCount)
            val weights = liveNodeGroupWeight.copyOf(entryCount)
            val groups = Array(groupCount) { groupIdToValue[it] }

            return GroupSnapshot(
                groupCount = groupCount,
                nodeCount = nodeCount,
                groupIds = groups,
                nodeGroupOffset = offset,
                nodeGroupId = ids,
                nodeGroupWeight = weights,
            )
        }
    }
}