package com.moly3.dataviz.core.graph.hull

import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.model.GroupId
import com.moly3.dataviz.core.graph.model.GroupIndex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Owns the background hull-computation pipeline.
 *
 * Lifecycle:
 *   - Created/remembered inside the Composable.
 *   - `submit()` is called from a `LaunchedEffect` that ticks at the
 *     configured interval, and from a one-shot effect on group-model change.
 *   - Coalesces bursts: only the *most recent* request is honoured; older ones
 *     are dropped. A slow hull computation can't pile up.
 *
 * Appearance (name/color) is resolved from the [GroupIndex] carried in the
 * request — no resolver lambdas. Because GroupIndex is built from an immutable
 * GroupModel, a name or color edit produces a different index and thus a
 * different request payload, with no signature hashing needed.
 */
class GroupHullController(
    private val ioContext: CoroutineContext,
) {
    private val _hulls = MutableStateFlow<ImmutableList<GroupHull>>(persistentListOf())
    val hulls: StateFlow<ImmutableList<GroupHull>> = _hulls

    // Conflated channel: only the latest signal survives.
    private val requestChannel = Channel<Request>(capacity = Channel.CONFLATED)
    private var worker: Job? = null

    private data class Request(
        val snapshot: List<Pair<GroupId, FloatArray>>,
        val index: GroupIndex<*>,
        val settings: GroupSettings,
    )

    fun start(scope: CoroutineScope) {
        if (worker?.isActive == true) return
        worker =
            scope.launch(ioContext) {
                for (req in requestChannel) {
                    if (!isActive) break
                    val result = computeAll(req)
                    _hulls.value = result
                }
            }
    }

    fun stop() {
        worker?.cancel()
        worker = null
    }

    /**
     * Submit a recompute request. Cheap; allocates the snapshot but defers
     * the expensive hull math to the worker. Last submission wins.
     */
    fun submit(
        engine: IGraphEngine<*, *>,
        index: GroupIndex<*>,
        settings: GroupSettings,
    ) {
        if (!settings.enabled) {
            _hulls.value = persistentListOf()
            return
        }
        // Snapshot must run on whatever thread step() last left things in.
        // It only reads stable arrays the engine isn't mutating between frames,
        // so this is safe to call from the main thread immediately after step().
        val snapshot = engine.snapshotGroupsForHulls()
        if (snapshot.isEmpty()) {
            _hulls.value = persistentListOf()
            return
        }
        requestChannel.trySend(Request(snapshot, index, settings))
    }

    private fun computeAll(req: Request): ImmutableList<GroupHull> {
        val out = ArrayList<GroupHull>(req.snapshot.size)
        for ((groupId, points) in req.snapshot) {
            val pointCount = points.size / 2
            val r =
                if (pointCount > req.settings.angularHullThreshold) {
                    // Large group — physics keeps it blob-shaped; angular sweep is safe.
                    AngularHullBuilder.build(
                        pointsXY = points,
                        sectors = req.settings.angularHullSectors,
                        padding = req.settings.hullPadding,
                    )
                } else {
                    // Small group — concave detail is cheap and worth keeping.
                    HullBuilder.build(
                        pointsXY = points,
                        k = req.settings.hullK,
                        padding = req.settings.hullPadding,
                        smoothing = req.settings.hullSmoothing,
                    )
                } ?: continue

            val def = req.index.defOf(groupId) ?: continue
            out.add(
                GroupHull(
                    groupId = groupId,
                    label = def.name,
                    color = def.color,
                    path = r.path,
                    labelAnchor = r.labelAnchor,
                ),
            )
        }
        return out.toImmutableList()
    }
}
