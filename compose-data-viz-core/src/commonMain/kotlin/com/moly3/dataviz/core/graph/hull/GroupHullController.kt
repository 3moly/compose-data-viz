package com.moly3.dataviz.core.graph.hull

import androidx.compose.ui.graphics.Color
import com.moly3.dataviz.core.graph.engine.IGraphEngine
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
 *   - `requestRecompute()` is called from a `LaunchedEffect` that ticks at the
 *     configured interval (or on demand).
 *   - Coalesces bursts: only the *most recent* request is honoured; older ones
 *     are dropped.  This means a slow hull computation can't pile up.
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
        val snapshot: List<Pair<String, FloatArray>>,
        val groupLabelOf: (String) -> String,
        val groupColorOf: (String) -> Color,
        val settings: GroupSettings,
    )

    fun start(scope: CoroutineScope) {
        if (worker?.isActive == true) return
        worker = scope.launch(ioContext) {
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
        groupLabelOf: (String) -> String,
        groupColorOf: (String) -> Color,
        settings: GroupSettings
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
        requestChannel.trySend(Request(snapshot, groupLabelOf, groupColorOf, settings))
    }

    private fun computeAll(req: Request): ImmutableList<GroupHull> {
        val out = ArrayList<GroupHull>(req.snapshot.size)
        for ((groupId, points) in req.snapshot) {
            val r = HullBuilder.build(
                pointsXY = points,
                k = req.settings.hullK,
                padding = req.settings.hullPadding,
                smoothing = req.settings.hullSmoothing,
            ) ?: continue
            out.add(
                GroupHull(
                    groupId = groupId,
                    color = req.groupColorOf(groupId),
                    path = r.path,
                    labelAnchor = r.labelAnchor,
                    label = req.groupLabelOf(groupId)
                )
            )
        }
        return out.toImmutableList()
    }
}