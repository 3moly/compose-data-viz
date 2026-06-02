package com.moly3.dataviz.core.graph.engine.impl.ultra

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.engine.DragNodeData
import com.moly3.dataviz.core.graph.engine.IGraphEngine
import com.moly3.dataviz.core.graph.func.avaliableCpuProcessors
import com.moly3.dataviz.core.graph.hull.GroupSettings
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import com.moly3.dataviz.core.graph.model.GroupId
import com.moly3.dataviz.core.graph.model.GroupIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance force-directed graph engine.
 *
 * Behavior is tuned via [config]; physics via the [GraphViewSettings] passed
 * to [step]. Pass a custom [UltraFastEngineConfig] for non-default heat /
 * decay / sleep behavior.
 *
 * Smoothness design
 * -----------------
 * Two complementary mechanisms keep motion buttery regardless of node count:
 *
 *   1. Velocity smoothing: raw physics velocity (velX/velY) is low-pass
 *      filtered into smoothedVelX/Y, and POSITIONS update from the smoothed
 *      version. Physics can converge in 2 frames on a 5-node graph; the user
 *      still sees a glide because the smoothed velocity ramps up and down.
 *
 *   2. Adaptive sub-stepping: small graphs have spare CPU. Rather than
 *      detuning forces, we split each frame into multiple integration passes
 *      so each on-screen frame moves nodes a small, perceptible amount.
 *      Sub-stepping disables itself above subStepNodeCeiling — large graphs
 *      look smooth naturally through dense pairwise interaction.
 *
 * Partial freeze (isMoving = false)
 * ---------------------------------
 * When global motion is paused but a node is being dragged, the engine runs a
 * tiny BFS-bounded sub-simulation on the dragged node's N-hop neighborhood.
 * Everything else stays frozen. Dragging through dense clusters shoves the
 * local neighbors aside instead of clipping through them — without unfreezing
 * the wider graph and undoing the user's "pause".
 *
 * Anti-clump
 * ----------
 * applyAntiClumpForce detects nodes with too many neighbors inside a small
 * radius and pushes them radially outward from the local centroid. Purely
 * corrective; scales with alpha; never fights a settled good layout.
 *
 * Anti-stuck (overlap sleep gate)
 * -------------------------------
 * ALL forces decay together with [alpha], so the layout stays balanced and
 * never drifts. The only stuck-overlap problem is that a slept graph runs
 * no physics at all, so coincident nodes can never separate. The fix is
 * purely a sleep gate: the graph is not allowed to fall asleep while any two
 * nodes overlap. While awake, the normal physics pushes them apart; once
 * clean, it sleeps.
 *
 * Group-snapshot consistency
 * --------------------------
 * The engine's group SoA arrays (nodeGroupOffset / nodeGroupId / ... ) are
 * mutated inside [syncGroupsInternal], which runs on the physics coroutine.
 * The hull controller reads group topology from a DIFFERENT coroutine. Reading
 * the live arrays directly is a data race: a reader can observe a half-
 * reallocated or half-filled buffer and produce empty / wrong hulls — the
 * "change one group and every hull blinks out for a frame" bug.
 *
 * Fix: [syncGroupsInternal] publishes an immutable [GroupSnapshot] atomically
 * at its very end, once every array is fully written. [snapshotGroupsForHulls]
 * reads ONLY that published snapshot. A reader therefore always sees a whole,
 * self-consistent group topology — either entirely the old one or entirely the
 * new one, never a torn mix.
 */
@OptIn(ExperimentalAtomicApi::class)
@Stable
class UltraFastEngine<Id, Data>(
    _config: UltraFastEngineConfig = UltraFastEngineConfig.Default,
) : IGraphEngine<Id, Data> {
    private var config: UltraFastEngineConfig = _config

    fun setNewConfig(newConfig: UltraFastEngineConfig) {
        config = newConfig
    }

    // -----------------------------------------------------------------
    // Magic-number constants
    // -----------------------------------------------------------------
    // Hard, structural numbers that are NOT user-tunable physics knobs (those
    // live in UltraFastEngineConfig / GraphViewSettings). These are hashing
    // primes, buffer-sizing minimums, bit masks, graph-size band edges, and
    // the fixed shaping ratios the solver relies on. Pulled into named
    // constants so intent is explicit and the values live in one place.
    private companion object {
        // Hashing / signature mixing
        const val HASH_MULTIPLIER = 31
        const val SIGNATURE_PRIME = 1_000_003

        // Graph-size bands (node counts) — used to soften physics on tiny graphs.
        const val SMALL_GRAPH_MAX_NODES = 30
        const val MEDIUM_GRAPH_MAX_NODES = 80

        // Reheat intensity scaling per size band.
        const val SMALL_GRAPH_REHEAT_SCALE = 0.5f
        const val MEDIUM_GRAPH_REHEAT_SCALE = 0.75f

        // Repulsion damping per size band.
        const val SMALL_GRAPH_REPEL_DAMPER = 0.7f
        const val MEDIUM_GRAPH_REPEL_DAMPER = 0.85f

        // Velocity damping per size band.
        const val SMALL_GRAPH_DAMPING_SCALE = 0.92f
        const val MEDIUM_GRAPH_DAMPING_SCALE = 0.96f

        // Heat (alpha) bands shared by rebuild cadence + timestep selection.
        const val ALPHA_HOT = 0.5f
        const val ALPHA_WARM = 0.2f
        const val ALPHA_COOL = 0.1f
        const val ALPHA_COLD = 0.05f

        // Quadtree rebuild cadence (frames between rebuilds) for the warm/cool bands.
        const val REBUILD_EVERY_WARM = 2
        const val REBUILD_EVERY_COOL = 4

        // Adaptive sub-stepping.
        const val SUBSTEP_SPARSITY_NUMERATOR = 60f
        const val MAX_SPARSITY_FACTOR = 4f
        const val SUBSTEP_ROUNDING_BIAS = 0.5f

        // Force-field shaping.
        const val SOFTENING_FRACTION = 0.5f
        const val MIDPOINT_FACTOR = 0.5f
        const val MIN_DISTANCE_SQ = 0.01f
        const val VELOCITY_SLEEP_EPSILON_SQ = 1e-5f
        const val WEIGHT_SUM_EPSILON = 1e-4f

        // Parallel work sizing.
        const val MIN_NODE_CHUNK_SIZE = 32
        const val MIN_EDGE_CHUNK_SIZE = 64
        const val TRAVERSAL_STACK_SIZE = 256
        const val FORCE_RESULT_SIZE = 2

        // Anti-clump applicability range + neighbor early-exit cap.
        const val ANTI_CLUMP_MIN_NODES = 4
        const val ANTI_CLUMP_MAX_NODES = 2000
        const val CLUMP_CAP_MULTIPLIER = 2

        // Hub / long-link shaping in the edge solver.
        const val HUB_DEGREE_THRESHOLD = 20
        const val HUB_DEGREE_FALLOFF = 0.02f
        const val HUB_DEGREE_MIN_SCALE = 0.5f
        const val LONG_LINK_DISTANCE_FACTOR = 1.5f

        // Deterministic jitter to break exact coincidence (edges + group separation).
        const val EDGE_JITTER_BASE = 0.01f
        const val EDGE_JITTER_STEP = 0.005f
        const val EDGE_JITTER_MODULO = 5
        const val GROUP_JITTER_BASE = 0.01f
        const val GROUP_JITTER_STEP = 0.1f
        const val GROUP_JITTER_MASK = 0xF

        // Overlap-detection grid limits.
        const val MIN_NODES_FOR_OVERLAP = 2
        const val MAX_GRID_CELLS = 4_000_000L
        const val MAX_BRUTE_FORCE_NODES = 4000

        // Buffer sizing + growth.
        const val MIN_ARRAY_CAPACITY = 16
        const val MIN_GROUP_CAPACITY = 8
        const val BUFFER_GROWTH_FACTOR = 2
        const val EDGE_COUNT_DIVISOR = 2
        const val GROUP_MAP_CAPACITY_FACTOR = 2

        // Adaptive decay pivot — decay rate is normalized around this node count.
        const val DECAY_PIVOT_NODE_COUNT = 300f

        // De-stacking coincident nodes (golden-angle spiral).
        const val QUANTIZE_FACTOR = 2f
        const val KEY_SHIFT_BITS = 32
        const val LOWER_32_BIT_MASK = 0xFFFFFFFFL
        const val GOLDEN_ANGLE_RADIANS = 2.3999632f
        const val SPIRAL_RANK_MASK = 0x7
        const val SPIRAL_RADIUS_STEP = 0.15f
    }

    // -----------------------------------------------------------------
    // SoA storage
    // -----------------------------------------------------------------
    // -----------------------------------------------------------------
    // Group data — CSR-style, keyed by node INDEX (built from GroupIndex).
    // nodeGroupId[k] / nodeGroupWeight[k] are parallel; nodeGroupOffset
    // partitions them per node.
    //
    // These arrays are the engine's WORKING buffers — over-allocated, mutated
    // in place, and only safe to touch from the physics coroutine. Nothing
    // outside step()/syncData()/syncGroupsInternal() may read them. Cross-
    // coroutine readers use `publishedGroupSnapshot` instead.
    // -----------------------------------------------------------------
    private var nodeGroupOffset = IntArray(1)
    private var nodeGroupId = IntArray(0)
    private var nodeGroupWeight = FloatArray(0)
    private var groupCount = 0
    private var groupIdToValue: Array<GroupId> = emptyArray()

    /** Number of CSR entries actually written by the last syncGroupsInternal. */
    private var groupEntryCount = 0

    private var groupCentroidX = FloatArray(0)
    private var groupCentroidY = FloatArray(0)
    private var groupMemberCount = IntArray(0)

    // Sum of weights per group — centroid is weight-averaged.
    private var groupWeightSum = FloatArray(0)

    /**
     * The single source of truth for ANY cross-coroutine group read.
     *
     * Written once per syncGroupsInternal (including the disable path), read by
     * snapshotGroupsForHulls. AtomicReference makes the swap a single atomic
     * publish — a reader gets the complete previous snapshot or the complete
     * new one, never a partially-built array.
     */
    private val publishedGroupSnapshot =
        AtomicReference<GroupSnapshot>(GroupSnapshot.EMPTY)

    /** -1 means "no signature yet" so the very first sync doesn't fire a fake reheat. */
    private var lastGroupSignature = -1

    @Volatile
    private var pendingGroupIndex: GroupIndex<Id>? = null

    @Volatile
    private var pendingGroupSettings: GroupSettings = GroupSettings()

    /**
     * Identity of the GroupIndex most recently handed to setGroupData.
     * syncGroupsInternal copies this into the published snapshot so a reader
     * can verify the snapshot was built from the index it expects.
     */
    @Volatile
    private var pendingGroupIndexIdentity: Int = 0

    /**
     * Identity actually baked into the last published snapshot. Read by
     * hasSyncedGroupIndex from any coroutine.
     */
    @Volatile
    private var publishedGroupIndexIdentity: Int = 0

    override fun setGroupData(
        groupIndex: GroupIndex<Id>?,
        settings: GroupSettings,
        groupIndexIdentity: Int,
        suppressReheat: Boolean,
    ) {
        // Identity + settings define logical equivalence. The GroupIndex reference
        // itself is allowed to differ (the composable rebuilds it via remember on
        // remount even when the underlying groupModel is unchanged).
        val sameIdentity = groupIndexIdentity == publishedGroupIndexIdentity
        val sameSettings = settings == pendingGroupSettings
        val nullnessMatches = (groupIndex == null) == (pendingGroupIndex == null)
        if (sameIdentity && sameSettings && nullnessMatches) {
            // Still refresh the pending reference so the next sync reads the
            // current object (defensive — published snapshot is unchanged).
            pendingGroupIndex = groupIndex
            return
        }

        pendingGroupIndex = groupIndex
        pendingGroupSettings = settings
        pendingGroupIndexIdentity = groupIndexIdentity
        if (!suppressReheat) nudge()
    }

    private var nodeCount = 0
    private var edgeCount = 0

    private var posX = FloatArray(0)
    private var posY = FloatArray(0)
    private var velX = FloatArray(0)
    private var velY = FloatArray(0)
    private var forceX = FloatArray(0)
    private var forceY = FloatArray(0)

    // Smoothed (low-pass-filtered) velocity. velX/velY hold raw physics
    // velocity; smoothedVelX/Y are what positions actually integrate from.
    // This is what makes small graphs glide instead of teleport — physics
    // can snap to equilibrium in 2 frames; the user sees a smooth ramp.
    private var smoothedVelX = FloatArray(0)
    private var smoothedVelY = FloatArray(0)

    private var degree = IntArray(0)

    private var hubScaleCache = FloatArray(0)
    private var lastHubExponent = Float.NaN

    // Edge list (each undirected edge once, a < b)
    private var edgeA = IntArray(0)
    private var edgeB = IntArray(0)

    // CSR for legacy / traversal
    private var connectionsOffset = IntArray(1)
    private var connectionsFlat = IntArray(0)

    // Per-thread force scatter buffers
    private var threadForceX: Array<FloatArray> = emptyArray()
    private var threadForceY: Array<FloatArray> = emptyArray()
    private var lastThreadCount = -1

    private val ids = ArrayList<Id>()
    private val idToIndex = HashMap<Id, Int>()

    private val quadTree = UltraFastQuadTree()

    private var frameCount = 0

    // -----------------------------------------------------------------
    // Heat state
    // -----------------------------------------------------------------
    var alpha = config.startAlpha
        private set
    private var alphaTarget = 0f
    private var alphaDecay = config.baseAlphaDecay

    private var totalKineticEnergy = 0f
    private var lastNodeCountSignature = 0

    /**
     * True when at least one pair of nodes is closer than the overlap radius.
     * Computed cheaply each frame via a uniform grid. Used ONLY as a sleep
     * gate — it never produces a force, so it cannot fight the layout.
     */
    private var hasOverlap = false

    @Volatile
    private var freezingEnabled: Boolean = true

    override val isAsleep: Boolean
        get() {
            if (!freezingEnabled) return false
            // Never report asleep while nodes are still stacked — otherwise
            // step() early-exits and they can never separate.
            if (hasOverlap) return false
            return alpha <= 0f ||
                (alpha < config.asleepAlphaCheck && totalKineticEnergy < config.sleepEnergyThreshold)
        }

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    override fun reheat() {
        reheatInternal(config.reheatAlpha)
    }

    /** Soft wake — for settings changes, single-node adds, post-drag settle. */
    override fun nudge() {
        reheatInternal(config.nudgeAlpha)
    }

    private fun reheatInternal(intensity: Float) {
        // Small graphs reach a new equilibrium in just a few frames. A full
        // reheat on a 5-node graph causes a visible "explosion" — nodes fly
        // apart, settle in 3 frames, done. Scale intensity down so motion lasts
        // long enough to be perceptible and feels deliberate rather than violent.
        val scaled =
            if (nodeCount in 1..SMALL_GRAPH_MAX_NODES) {
                intensity * SMALL_GRAPH_REHEAT_SCALE
            } else if (nodeCount in (SMALL_GRAPH_MAX_NODES + 1)..MEDIUM_GRAPH_MAX_NODES) {
                intensity * MEDIUM_GRAPH_REHEAT_SCALE
            } else {
                intensity
            }
        alpha = max(alpha, scaled)
        alphaTarget = 0f
    }

    // -----------------------------------------------------------------
    // step()
    // -----------------------------------------------------------------

    override suspend fun step(
        graphNodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        draggedNode: DragNodeData<Id>?,
        isMoving: Boolean,
        moveConnectedWhenPaused: Boolean,
    ) = coroutineScope {
        // ---- Structural change detection ----
        val structureSig =
            run {
                var h = graphNodes.size
                for (node in graphNodes) {
                    val list = connections[node.id]
                    h = h * HASH_MULTIPLIER + node.id.hashCode()
                    if (list != null) {
                        h = h * HASH_MULTIPLIER + list.size
                        for (tid in list) h = h * HASH_MULTIPLIER + tid.hashCode()
                    } else {
                        h *= HASH_MULTIPLIER
                    }
                }
                h
            }
        val structureChanged = structureSig != lastNodeCountSignature
        if (structureChanged) {
            val prevNodeCount = nodeCount
            val newNodeCount = graphNodes.size
            val delta = abs(newNodeCount - prevNodeCount)
            val bigThreshold = (newNodeCount * config.bigChangeFraction).toInt().coerceAtLeast(1)

            when {
                prevNodeCount == 0 -> {
                    // first load — no reheat needed
                }

                delta <= config.gentleAddThreshold -> {
                    nudge()
                }

                delta <= bigThreshold -> {
                    reheatInternal(config.moderateChangeAlpha)
                }

                else -> {
                    reheatInternal(config.reheatAlpha)
                }
            }
            lastNodeCountSignature = structureSig
        }

        if (draggedNode != null && isMoving) reheatInternal(config.dragReheatAlpha)

        // -----------------------------------------------------------------
        // Paused-but-dragging path: local sub-simulation only.
        // -----------------------------------------------------------------
        // When the user pauses global motion (isMoving=false) but is still
        // dragging a node, we want the dragged node's local neighborhood to
        // respond — pushing into a cluster should shove the cluster aside,
        // not clip through it. The rest of the graph stays frozen.
        if (!isMoving) {
            syncData(graphNodes, coordinates, velocities, connections, structureChanged)

            val draggedIdx = draggedNode?.id?.let { idToIndex[it] } ?: -1
            val hasDrag = draggedIdx >= 0 && draggedNode?.offset != null

            when {
                hasDrag && moveConnectedWhenPaused -> {
                    // Local sub-simulation around the dragged node — pushing into a
                    // cluster shoves neighbors aside instead of clipping through them.
                    runPartialFreezeStep(settings, draggedNode!!, draggedIdx)
                }

                hasDrag && !moveConnectedWhenPaused -> {
                    // Lone-drag mode: ONLY the dragged node moves. Snap it to the
                    // cursor and leave every other node and velocity untouched.
                    // This is the "rigid graph, drag-one-pin" feel — useful when
                    // the user is curating a manual layout and doesn't want their
                    // arrangement perturbed by a drag.
                    posX[draggedIdx] = draggedNode!!.offset!!.x
                    posY[draggedIdx] = draggedNode.offset!!.y
                    velX[draggedIdx] = 0f
                    velY[draggedIdx] = 0f
                    smoothedVelX[draggedIdx] = 0f
                    smoothedVelY[draggedIdx] = 0f
                    // NOTE: deliberately do NOT zero other nodes' velocities here.
                    // They were already zeroed when isMoving flipped to false; touching
                    // them again every frame would be wasted work on large graphs.
                }

                else -> {
                    // True freeze — no drag, no motion. Zero velocities and park heat
                    // so re-enabling isMoving later doesn't unleash stale velocity.
                    for (i in 0 until nodeCount) {
                        velX[i] = 0f
                        velY[i] = 0f
                        smoothedVelX[i] = 0f
                        smoothedVelY[i] = 0f
                    }
                    alpha = 0f
                    alphaTarget = 0f
                    totalKineticEnergy = 0f
                }
            }

            // Write back — same loop as the normal path.
            for (i in 0 until nodeCount) {
                val id = ids[i]
                coordinates[id] = Offset(posX[i], posY[i])
                velocities[id] = Offset(velX[i], velY[i])
            }
            return@coroutineScope
        }

        // Early exit if truly idle AND nothing is overlapping AND nothing changed.
        if (isAsleep && draggedNode == null && !structureChanged) {
            syncData(graphNodes, coordinates, velocities, connections, false)
            return@coroutineScope
        }

        syncData(graphNodes, coordinates, velocities, connections, structureChanged)
        frameCount++

        if (settings.hubExpansionExponent != lastHubExponent) {
            recomputeHubScale(settings.hubExpansionExponent)
            lastHubExponent = settings.hubExpansionExponent
        }

        // -----------------------------------------------------------------
        // Adaptive sub-stepping — the smoothness engine
        // -----------------------------------------------------------------
        // Rather than scaling forces down on small graphs (which detunes
        // physics), we keep physics at full strength and SPLIT each frame into
        // multiple integration passes. Each sub-step moves nodes a small,
        // perceptible amount. CPU budget is bounded: large graphs never
        // sub-step (their many pairwise interactions are already smooth).
        //
        // The number of sub-steps is chosen by predicting per-frame displacement:
        // hotter alpha + smaller graphs = more sub-steps.
        // -----------------------------------------------------------------
        val subSteps =
            if (nodeCount > config.subStepNodeCeiling) {
                1
            } else {
                // Heat 1.0 + small graph (sparsity 4) = 4 sub-steps. Cools off as alpha
                // drops, so settled graphs don't waste CPU.
                val heatFactor = alpha.coerceIn(0f, 1f)
                val sparsityFactor =
                    (SUBSTEP_SPARSITY_NUMERATOR / nodeCount.coerceAtLeast(1)).coerceAtMost(MAX_SPARSITY_FACTOR)
                val raw = heatFactor * sparsityFactor
                (raw + SUBSTEP_ROUNDING_BIAS).toInt().coerceIn(1, config.maxSubSteps)
            }

        // Adaptive quadtree rebuild — unchanged cadence
        val rebuildEvery =
            when {
                alpha > ALPHA_WARM -> 1
                alpha > ALPHA_COLD -> REBUILD_EVERY_WARM
                else -> REBUILD_EVERY_COOL
            }

        val draggedIdx = draggedNode?.id?.let { idToIndex[it] } ?: -1
        val theta =
            if (nodeCount > config.bigGraphNodeCount) config.thetaLargeGraph else config.thetaSmallGraph
        val thetaSq = theta * theta
        val softening = settings.circleSize * SOFTENING_FRACTION

        // ALL forces decay together with alpha — symmetry preserves balance.
        // Repulsion is softened on small graphs because there's no "crowd" to
        // dilute the inverse-square field; without this, 5 nodes blast apart.
        val repelDamper =
            if (nodeCount < SMALL_GRAPH_MAX_NODES) {
                SMALL_GRAPH_REPEL_DAMPER
            } else if (nodeCount < MEDIUM_GRAPH_MAX_NODES) {
                MEDIUM_GRAPH_REPEL_DAMPER
            } else {
                1f
            }
        val effectiveRepel = settings.repelForce * alpha * repelDamper
        val effectiveLink = settings.linkForce * alpha
        val effectiveCenter = settings.centerForce * alpha

        val cores = (avaliableCpuProcessors(settings.cpuCores) - 1).coerceAtLeast(1)
        ensureThreadBuffers(cores, nodeCount)

        val baseDamping =
            settings.dampingFactor.let {
                if (it == 0f) config.fallbackDamping else it.coerceAtMost(config.maxDamping)
            }
        // Extra damping on small graphs — they have no inherent "crowd friction"
        // from neighbor repulsion, so without this they visibly oscillate around
        // equilibrium. Larger graphs damp themselves implicitly through dense
        // pairwise interaction.
        val damping =
            if (nodeCount < SMALL_GRAPH_MAX_NODES) {
                baseDamping * SMALL_GRAPH_DAMPING_SCALE
            } else if (nodeCount < MEDIUM_GRAPH_MAX_NODES) {
                baseDamping * MEDIUM_GRAPH_DAMPING_SCALE
            } else {
                baseDamping
            }

        // Per-frame timestep selection (then divided across sub-steps).
        val baseTimestep =
            when {
                alpha > ALPHA_HOT -> config.timestepHot
                alpha > ALPHA_WARM -> config.timestepWarm
                alpha > ALPHA_COOL -> config.timestepCool
                else -> config.timestepCold
            }
        val perStepTimestep = baseTimestep / subSteps
        // Damping is per-step^subSteps = per-frame, so equivalent total damping
        // is preserved no matter how many sub-steps we run.
        val perStepDamping = damping.pow(1f / subSteps)

        // posScale also gets the sub-step division so total per-frame displacement
        // is preserved — we're splitting one step into many, not adding motion.
        val framePosScale =
            (config.posUpdateBlend + alpha * config.posUpdateAlphaScale) *
                config.globalMotionScale
        val perStepPosScale = framePosScale / subSteps

        val maxF = settings.maxForce * (1f + alpha)
        val maxFSq = maxF * maxF
        val nodeChunkSize = max(MIN_NODE_CHUNK_SIZE, (nodeCount + cores - 1) / cores)
        val nodeChunks = (nodeCount + nodeChunkSize - 1) / nodeChunkSize
        val chunkEnergy = FloatArray(nodeChunks)

        // -----------------------------------------------------------------
        // Sub-step loop — each iteration is a full force + integrate pass.
        // For N > subStepNodeCeiling, subSteps == 1 so this is identical to
        // the original single-pass behavior.
        // -----------------------------------------------------------------
        for (subStep in 0 until subSteps) {
            val isLastSubStep = subStep == subSteps - 1

            // Rebuild quadtree only on the FIRST sub-step (or per the normal
            // cadence). Positions within a single frame don't change enough to
            // justify rebuilding every sub-step — Barnes-Hut is forgiving.
            if (subStep == 0 && (frameCount % rebuildEvery == 0 || frameCount == 1)) {
                quadTree.build(posX, posY, nodeCount)
            }

            for (t in 0 until cores) {
                threadForceX[t].fill(0f, 0, nodeCount)
                threadForceY[t].fill(0f, 0, nodeCount)
            }

            // ---- PHASE 1: Repulsion (Barnes-Hut) + centering ----
            val repelJobs =
                (0 until nodeChunks).map { chunkIdx ->
                    async(Dispatchers.Default) {
                        val start = chunkIdx * nodeChunkSize
                        val end = min(start + nodeChunkSize, nodeCount)
                        val traversalStack = IntArray(TRAVERSAL_STACK_SIZE)
                        val forceResult = FloatArray(FORCE_RESULT_SIZE)

                        for (i in start until end) {
                            if (i == draggedIdx) {
                                forceX[i] = 0f
                                forceY[i] = 0f
                                continue
                            }
                            quadTree.computeRepulsionIterative(
                                posX[i],
                                posY[i],
                                i,
                                effectiveRepel,
                                softening,
                                thetaSq,
                                traversalStack,
                                forceResult,
                            )
                            var fx = forceResult[0]
                            var fy = forceResult[1]

                            val px = posX[i]
                            val py = posY[i]
                            val distFromCenterSq = px * px + py * py
                            if (distFromCenterSq > MIN_DISTANCE_SQ) {
                                fx -= px * effectiveCenter
                                fy -= py * effectiveCenter
                            }
                            forceX[i] = fx
                            forceY[i] = fy
                        }
                    }
                }
            repelJobs.awaitAll()

            // ---- PHASE 1.5: Group magnetization ----
            val gs = pendingGroupSettings
            if (gs.enabled && groupCount > 0 && (gs.cohesionForce > 0f || gs.groupSeparation > 0f)) {
                applyGroupForces(gs, draggedIdx)
            }

            // ---- PHASE 1.75: Anti-clump spread ----
            // Detect nodes with many neighbors inside the clump radius and push
            // them radially outward from the local centroid. Purely corrective.
            if (nodeCount in ANTI_CLUMP_MIN_NODES..ANTI_CLUMP_MAX_NODES) {
                applyAntiClumpForce(settings)
            }

            // ---- PHASE 2: Edge spring forces ----
            if (edgeCount > 0) {
                applyEdgeForces(
                    cores,
                    settings,
                    effectiveLink,
                    effectiveRepel,
                    softening,
                    draggedIdx,
                )

                // Reduce per-thread buffers
                val reduceJobs =
                    (0 until nodeChunks).map { chunkIdx ->
                        async(Dispatchers.Default) {
                            val start = chunkIdx * nodeChunkSize
                            val end = min(start + nodeChunkSize, nodeCount)
                            for (i in start until end) {
                                if (i == draggedIdx) continue
                                var fx = forceX[i]
                                var fy = forceY[i]
                                for (t in 0 until cores) {
                                    fx += threadForceX[t][i]
                                    fy += threadForceY[t][i]
                                }
                                forceX[i] = fx
                                forceY[i] = fy
                            }
                        }
                    }
                reduceJobs.awaitAll()
            }

            // ---- PHASE 3: Clamp + integrate + smooth ----
            // Only the FINAL sub-step accumulates kinetic energy — earlier
            // sub-steps are transient and would falsely inflate the sleep check.
            if (isLastSubStep) {
                for (c in chunkEnergy.indices) chunkEnergy[c] = 0f
            }

            val integrateJobs =
                (0 until nodeChunks).map { chunkIdx ->
                    async(Dispatchers.Default) {
                        val start = chunkIdx * nodeChunkSize
                        val end = min(start + nodeChunkSize, nodeCount)
                        var localEnergy = 0f
                        val smoothing = config.velocitySmoothing
                        val perStepCap = config.maxDisplacementPerFrame / subSteps
                        val perStepCapSq = perStepCap * perStepCap

                        for (i in start until end) {
                            if (i == draggedIdx) continue
                            var fx = forceX[i]
                            var fy = forceY[i]

                            val fMagSq = fx * fx + fy * fy
                            if (fMagSq > maxFSq) {
                                val s = maxF / sqrt(fMagSq)
                                fx *= s
                                fy *= s
                            }

                            // Integrate raw physics velocity.
                            var vx = (velX[i] + fx * perStepTimestep) * perStepDamping
                            var vy = (velY[i] + fy * perStepTimestep) * perStepDamping
                            val vMagSq = vx * vx + vy * vy
                            if (vMagSq < VELOCITY_SLEEP_EPSILON_SQ) {
                                vx = 0f
                                vy = 0f
                            } else if (isLastSubStep) {
                                localEnergy += vMagSq
                            }
                            velX[i] = vx
                            velY[i] = vy

                            // Low-pass filter into the *applied* velocity. This is
                            // what creates the smooth, cinematic motion regardless
                            // of node count: physics can snap, but the user sees a
                            // heavily smoothed version.
                            val sx = smoothedVelX[i] * (1f - smoothing) + vx * smoothing
                            val sy = smoothedVelY[i] * (1f - smoothing) + vy * smoothing
                            smoothedVelX[i] = sx
                            smoothedVelY[i] = sy

                            // Per-step displacement, hard-capped. Safety net for
                            // pathological cases (huge forces during reheat);
                            // normally sub-stepping keeps us well under it.
                            var dxStep = sx * perStepPosScale
                            var dyStep = sy * perStepPosScale
                            val stepMagSq = dxStep * dxStep + dyStep * dyStep
                            if (stepMagSq > perStepCapSq) {
                                val s = perStepCap / sqrt(stepMagSq)
                                dxStep *= s
                                dyStep *= s
                            }
                            posX[i] += dxStep
                            posY[i] += dyStep
                        }
                        if (isLastSubStep) chunkEnergy[chunkIdx] = localEnergy
                    }
                }
            integrateJobs.awaitAll()

            // Dragged node override — applied every sub-step so it tracks the
            // cursor without lag even when sub-stepping is active.
            if (draggedIdx >= 0 && draggedNode?.offset != null) {
                posX[draggedIdx] = draggedNode.offset.x
                posY[draggedIdx] = draggedNode.offset.y
                velX[draggedIdx] = 0f
                velY[draggedIdx] = 0f
                smoothedVelX[draggedIdx] = 0f
                smoothedVelY[draggedIdx] = 0f
            }
        }
        // -----------------------------------------------------------------
        // End sub-step loop
        // -----------------------------------------------------------------

        // ---- Overlap detection (sleep gate only — produces no force) ----
        hasOverlap = detectOverlap(settings)

        // Heat decay + snap-freeze
        totalKineticEnergy = chunkEnergy.sum()
        alpha += (alphaTarget - alpha) * alphaDecay

        if (freezingEnabled) {
            if (hasOverlap) {
                alpha = max(alpha, config.overlapResolveAlpha)
            } else if (alpha < config.sleepAlphaThreshold &&
                totalKineticEnergy < config.sleepEnergyThreshold
            ) {
                alpha = 0f
                // Zero smoothed velocity at sleep too — otherwise the smoothed
                // value would tail off slowly even after physics is frozen.
                for (i in 0 until nodeCount) {
                    smoothedVelX[i] = 0f
                    smoothedVelY[i] = 0f
                }
            }
        } else {
            alpha = max(alpha, config.minAlphaWhenUnfrozen)
        }

        // Write back
        for (i in 0 until nodeCount) {
            val id = ids[i]
            coordinates[id] = Offset(posX[i], posY[i])
            velocities[id] = Offset(velX[i], velY[i])
        }
    }

    // -----------------------------------------------------------------
    // Partial-freeze physics — runs when isMoving=false AND a node is dragged.
    // -----------------------------------------------------------------

    /**
     * Run a tiny simulation on the N-hop neighborhood around the dragged
     * node only. Everything else stays fixed.
     *
     * Why this exists: when isMoving=false, the user still expects dragging
     * to "feel alive" — pushing a node into a cluster should shove the cluster
     * aside locally, not slide the dragged node over its neighbors as if
     * they were static obstacles. But unfreezing the whole graph for a single
     * drag would defeat the point of pausing.
     *
     * Implementation: BFS the neighborhood, run a cheap repulsion + spring
     * pass on those nodes only, integrate with the same velocity-smoothing
     * as the main loop. No quadtree, no parallel scatter — the neighborhood
     * is small by construction (a few dozen nodes for hops=2 in normal
     * graphs).
     */
    private fun runPartialFreezeStep(
        settings: GraphViewSettings,
        draggedNode: DragNodeData<Id>,
        draggedIdx: Int,
    ) {
        // BFS to collect the active neighborhood.
        val hops = config.dragNeighborhoodHops.coerceAtLeast(0)
        val active = IntArray(nodeCount)
        val visited = BooleanArray(nodeCount)
        val hopOf = IntArray(nodeCount) { -1 }
        var head = 0
        var tail = 0
        active[tail++] = draggedIdx
        visited[draggedIdx] = true
        hopOf[draggedIdx] = 0

        while (head < tail) {
            val u = active[head++]
            val h = hopOf[u]
            if (h >= hops) continue
            val from = connectionsOffset[u]
            val to = connectionsOffset[u + 1]
            for (k in from until to) {
                val v = connectionsFlat[k]
                if (!visited[v]) {
                    visited[v] = true
                    hopOf[v] = h + 1
                    active[tail++] = v
                }
            }
        }
        val activeCount = tail

        // Pin the dragged node to the cursor first so all forces see it
        // at the correct position.
        if (draggedNode.offset != null) {
            posX[draggedIdx] = draggedNode.offset.x
            posY[draggedIdx] = draggedNode.offset.y
            velX[draggedIdx] = 0f
            velY[draggedIdx] = 0f
            smoothedVelX[draggedIdx] = 0f
            smoothedVelY[draggedIdx] = 0f
        }

        val localAlpha = config.partialDragAlpha
        val effRepel = settings.repelForce * localAlpha
        val effLink = settings.linkForce * localAlpha
        val softening = settings.circleSize * SOFTENING_FRACTION
        val softeningSq = softening * softening
        val damping =
            settings.dampingFactor.let {
                if (it == 0f) config.fallbackDamping else it.coerceAtMost(config.maxDamping)
            }
        val timestep = config.timestepCool
        val maxF = settings.maxForce
        val maxFSq = maxF * maxF
        val posBlend =
            (config.posUpdateBlend + localAlpha * config.posUpdateAlphaScale) *
                config.globalMotionScale
        // Zero forces on active set.
        for (idx in 0 until activeCount) {
            val i = active[idx]
            forceX[i] = 0f
            forceY[i] = 0f
        }

        // O(k^2) pairwise repulsion within the active set — k is small.
        for (a in 0 until activeCount) {
            val i = active[a]
            if (i == draggedIdx) continue
            val px = posX[i]
            val py = posY[i]
            var fx = 0f
            var fy = 0f
            for (b in 0 until activeCount) {
                if (a == b) continue
                val j = active[b]
                val dx = px - posX[j]
                val dy = py - posY[j]
                val distSq = max(dx * dx + dy * dy, softeningSq)
                val mag = effRepel / distSq
                fx += dx * mag
                fy += dy * mag
            }
            forceX[i] = fx
            forceY[i] = fy
        }

        // Spring forces on edges with both ends in active set (each edge once).
        for (a in 0 until activeCount) {
            val i = active[a]
            val from = connectionsOffset[i]
            val to = connectionsOffset[i + 1]
            for (k in from until to) {
                val j = connectionsFlat[k]
                if (!visited[j] || j <= i) continue
                val dx = posX[j] - posX[i]
                val dy = posY[j] - posY[i]
                val dist = sqrt(max(dx * dx + dy * dy, MIN_DISTANCE_SQ))
                val displacement = dist - settings.linkDistance
                val mag = effLink * displacement / dist
                val fx = dx * mag
                val fy = dy * mag
                if (i != draggedIdx) {
                    forceX[i] += fx
                    forceY[i] += fy
                }
                if (j != draggedIdx) {
                    forceX[j] -= fx
                    forceY[j] -= fy
                }
            }
        }

        // Integrate with smoothing — same low-pass as the main loop.
        val smoothing = config.velocitySmoothing
        val perStepCap = config.maxDisplacementPerFrame
        val perStepCapSq = perStepCap * perStepCap
        for (idx in 0 until activeCount) {
            val i = active[idx]
            if (i == draggedIdx) continue
            var fx = forceX[i]
            var fy = forceY[i]
            val fMagSq = fx * fx + fy * fy
            if (fMagSq > maxFSq) {
                val s = maxF / sqrt(fMagSq)
                fx *= s
                fy *= s
            }
            var vx = (velX[i] + fx * timestep) * damping
            var vy = (velY[i] + fy * timestep) * damping
            if (vx * vx + vy * vy < VELOCITY_SLEEP_EPSILON_SQ) {
                vx = 0f
                vy = 0f
            }
            velX[i] = vx
            velY[i] = vy

            val sx = smoothedVelX[i] * (1f - smoothing) + vx * smoothing
            val sy = smoothedVelY[i] * (1f - smoothing) + vy * smoothing
            smoothedVelX[i] = sx
            smoothedVelY[i] = sy

            var dxStep = sx * posBlend
            var dyStep = sy * posBlend
            val stepMagSq = dxStep * dxStep + dyStep * dyStep
            if (stepMagSq > perStepCapSq) {
                val s = perStepCap / sqrt(stepMagSq)
                dxStep *= s
                dyStep *= s
            }
            posX[i] += dxStep
            posY[i] += dyStep
        }

        // Nodes outside the active set: zero their velocities so re-enabling
        // isMoving later doesn't unleash stale velocity into a settled layout.
        for (i in 0 until nodeCount) {
            if (!visited[i]) {
                velX[i] = 0f
                velY[i] = 0f
                smoothedVelX[i] = 0f
                smoothedVelY[i] = 0f
            }
        }
    }

    // -----------------------------------------------------------------
    // Anti-clump force — spreads dense clusters.
    // -----------------------------------------------------------------

    /**
     * When too many nodes fall inside one node's local radius, push that node
     * away from the centroid of its crowders. Scales with alpha so it dies
     * with the rest of the layout and never fights a settled good one.
     *
     * O(n^2) over the full node set — cheap up to a couple thousand nodes
     * and not worth quadtree complexity for this corrective force.
     */
    private fun applyAntiClumpForce(settings: GraphViewSettings) {
        val radius = settings.circleSize * config.clumpDetectRadiusMul
        val radiusSq = radius * radius
        val threshold = config.clumpNeighborThreshold
        val force = config.clumpSpreadForce * alpha * settings.repelForce
        if (force <= 0f) return

        val cap = threshold * CLUMP_CAP_MULTIPLIER // early-exit cap: we only need "enough" neighbors

        for (i in 0 until nodeCount) {
            val px = posX[i]
            val py = posY[i]
            var cx = 0f
            var cy = 0f
            var count = 0
            for (j in 0 until nodeCount) {
                if (i == j) continue
                val dx = posX[j] - px
                val dy = posY[j] - py
                if (dx * dx + dy * dy < radiusSq) {
                    cx += posX[j]
                    cy += posY[j]
                    count++
                    if (count > cap) break
                }
            }
            if (count >= threshold) {
                cx /= count
                cy /= count
                val ox = px - cx
                val oy = py - cy
                val distSq = max(ox * ox + oy * oy, 1f)
                val mag = force / distSq
                forceX[i] += ox * mag
                forceY[i] += oy * mag
            }
        }
    }

    // -----------------------------------------------------------------
    // Overlap detection — cheap uniform grid, single pass, no force output.
    // Returns true as soon as ANY pair is closer than the overlap radius.
    // -----------------------------------------------------------------

    private fun detectOverlap(settings: GraphViewSettings): Boolean {
        if (nodeCount < MIN_NODES_FOR_OVERLAP) return false

        val minDist = settings.circleSize * config.overlapDistanceMultiplier
        if (minDist <= 0f) return false
        val minDistSq = minDist * minDist

        // Bounding box
        var minX = posX[0]
        var maxX = posX[0]
        var minY = posY[0]
        var maxY = posY[0]
        for (i in 1 until nodeCount) {
            val x = posX[i]
            val y = posY[i]
            if (x < minX) {
                minX = x
            } else if (x > maxX) {
                maxX = x
            }
            if (y < minY) {
                minY = y
            } else if (y > maxY) {
                maxY = y
            }
        }

        val cell = max(minDist, 1f)
        val cols = (((maxX - minX) / cell).toInt() + 1).coerceAtLeast(1)
        val rows = (((maxY - minY) / cell).toInt() + 1).coerceAtLeast(1)

        // Spread-out graphs: a huge grid is pointless and overlaps are rare.
        // Fall back to a capped O(n^2) scan that bails on the first hit.
        if (cols.toLong() * rows.toLong() > MAX_GRID_CELLS) {
            if (nodeCount > MAX_BRUTE_FORCE_NODES) return false
            for (i in 0 until nodeCount) {
                val px = posX[i]
                val py = posY[i]
                for (j in i + 1 until nodeCount) {
                    val dx = px - posX[j]
                    val dy = py - posY[j]
                    if (dx * dx + dy * dy < minDistSq) return true
                }
            }
            return false
        }

        val cellHead = IntArray(cols * rows) { -1 }
        val nextInCell = IntArray(nodeCount)
        for (i in 0 until nodeCount) {
            val cx = (((posX[i] - minX) / cell).toInt()).coerceIn(0, cols - 1)
            val cy = (((posY[i] - minY) / cell).toInt()).coerceIn(0, rows - 1)
            val c = cy * cols + cx
            nextInCell[i] = cellHead[c]
            cellHead[c] = i
        }

        for (i in 0 until nodeCount) {
            val cx = (((posX[i] - minX) / cell).toInt()).coerceIn(0, cols - 1)
            val cy = (((posY[i] - minY) / cell).toInt()).coerceIn(0, rows - 1)
            val px = posX[i]
            val py = posY[i]
            for (gy in (cy - 1)..(cy + 1)) {
                if (gy < 0 || gy >= rows) continue
                for (gx in (cx - 1)..(cx + 1)) {
                    if (gx < 0 || gx >= cols) continue
                    var j = cellHead[gy * cols + gx]
                    while (j != -1) {
                        if (j > i) {
                            val dx = px - posX[j]
                            val dy = py - posY[j]
                            if (dx * dx + dy * dy < minDistSq) return true
                        }
                        j = nextInCell[j]
                    }
                }
            }
        }
        return false
    }

    // -----------------------------------------------------------------
    // Extracted force phases
    // -----------------------------------------------------------------

    private fun applyGroupForces(
        gs: GroupSettings,
        draggedIdx: Int,
    ) {
        for (g in 0 until groupCount) {
            groupCentroidX[g] = 0f
            groupCentroidY[g] = 0f
            groupMemberCount[g] = 0
            groupWeightSum[g] = 0f
        }
        for (i in 0 until nodeCount) {
            val from = nodeGroupOffset[i]
            val to = nodeGroupOffset[i + 1]
            val px = posX[i]
            val py = posY[i]
            for (k in from until to) {
                val gid = nodeGroupId[k]
                val wgt = nodeGroupWeight[k]
                groupCentroidX[gid] += px * wgt
                groupCentroidY[gid] += py * wgt
                groupWeightSum[gid] += wgt
                groupMemberCount[gid]++
            }
        }
        for (g in 0 until groupCount) {
            val ws = groupWeightSum[g]
            if (ws > WEIGHT_SUM_EPSILON) {
                val inv = 1f / ws
                groupCentroidX[g] *= inv
                groupCentroidY[g] *= inv
            }
        }

        val sepX = FloatArray(groupCount)
        val sepY = FloatArray(groupCount)
        val sepForce = gs.groupSeparation * alpha
        if (sepForce > 0f && groupCount > 1) {
            val soft = gs.groupSeparationSoftening
            val softSq = soft * soft
            for (a in 0 until groupCount) {
                if (groupMemberCount[a] == 0) continue
                for (b in a + 1 until groupCount) {
                    if (groupMemberCount[b] == 0) continue
                    var dx = groupCentroidX[a] - groupCentroidX[b]
                    var dy = groupCentroidY[a] - groupCentroidY[b]
                    var distSq = dx * dx + dy * dy
                    if (distSq < MIN_DISTANCE_SQ) {
                        dx = ((a - b) and GROUP_JITTER_MASK).toFloat() * GROUP_JITTER_STEP + GROUP_JITTER_BASE
                        dy = ((a + b) and GROUP_JITTER_MASK).toFloat() * GROUP_JITTER_STEP + GROUP_JITTER_BASE
                        distSq = dx * dx + dy * dy
                    }
                    val mag = sepForce / max(distSq, softSq)
                    val nx = dx * mag
                    val ny = dy * mag
                    sepX[a] += nx
                    sepY[a] += ny
                    sepX[b] -= nx
                    sepY[b] -= ny
                }
            }
        }

        val cohesion = gs.cohesionForce * alpha
        for (i in 0 until nodeCount) {
            if (i == draggedIdx) continue
            val from = nodeGroupOffset[i]
            val to = nodeGroupOffset[i + 1]
            if (from == to) continue
            val px = posX[i]
            val py = posY[i]
            var fx = 0f
            var fy = 0f
            val memberships = to - from
            for (k in from until to) {
                val gid = nodeGroupId[k]
                val wgt = nodeGroupWeight[k]
                // Cohesion pull scales by this node's membership weight:
                // a loose member (weight < 1) is pulled in more gently.
                fx += (groupCentroidX[gid] - px) * cohesion * wgt
                fy += (groupCentroidY[gid] - py) * cohesion * wgt
                // Separation is a group-level push; weight does not apply.
                fx += sepX[gid]
                fy += sepY[gid]
            }
            val invM = 1f / memberships
            forceX[i] += fx * invM
            forceY[i] += fy * invM
        }
    }

    private suspend fun applyEdgeForces(
        cores: Int,
        settings: GraphViewSettings,
        effectiveLink: Float,
        effectiveRepel: Float,
        softening: Float,
        draggedIdx: Int,
    ) = coroutineScope {
        val edgeChunkSize = max(MIN_EDGE_CHUNK_SIZE, (edgeCount + cores - 1) / cores)
        val edgeChunks = (edgeCount + edgeChunkSize - 1) / edgeChunkSize

        val baseLinkDistance = settings.linkDistance
        val longMul = settings.longDistanceLinkMultiplier
        val connRepulsionMul = settings.connectedRepulsionMultiplier
        val antiStickDist = settings.circleSize * config.antiStickDistanceMultiplier
        val antiStickForce = effectiveRepel * config.antiStickForceMultiplier

        val linkJobs =
            (0 until edgeChunks).map { chunkIdx ->
                async(Dispatchers.Default) {
                    val start = chunkIdx * edgeChunkSize
                    val end = min(start + edgeChunkSize, edgeCount)
                    val tIdx = chunkIdx % cores
                    val tfx = threadForceX[tIdx]
                    val tfy = threadForceY[tIdx]

                    for (e in start until end) {
                        val a = edgeA[e]
                        val b = edgeB[e]
                        if (a == draggedIdx && b == draggedIdx) continue

                        var dx = posX[b] - posX[a]
                        var dy = posY[b] - posY[a]
                        if (dx == 0f && dy == 0f) {
                            dx = EDGE_JITTER_BASE + (a % EDGE_JITTER_MODULO) * EDGE_JITTER_STEP
                            dy = EDGE_JITTER_BASE + (b % EDGE_JITTER_MODULO) * EDGE_JITTER_STEP
                        }

                        val distSq = max(dx * dx + dy * dy, MIN_DISTANCE_SQ)
                        val dist = sqrt(distSq)
                        val invDist = 1f / dist

                        val degA = degree[a]
                        val degB = degree[b]
                        val degSum = (degA + degB).coerceAtLeast(1)
                        val biasA = degB.toFloat() / degSum.toFloat()
                        val biasB = degA.toFloat() / degSum.toFloat()

                        val hubScale = (hubScaleCache[a] + hubScaleCache[b]) * MIDPOINT_FACTOR
                        val degreeScale =
                            if (degSum > HUB_DEGREE_THRESHOLD) {
                                (1f - (degSum - HUB_DEGREE_THRESHOLD) * HUB_DEGREE_FALLOFF)
                                    .coerceAtLeast(HUB_DEGREE_MIN_SCALE)
                            } else {
                                1f
                            }

                        val effectiveLinkDistance = baseLinkDistance * hubScale * degreeScale
                        val distMul = if (dist > effectiveLinkDistance * LONG_LINK_DISTANCE_FACTOR) longMul else 1f
                        val repCompensation =
                            (effectiveRepel / max(distSq, softening)) * connRepulsionMul

                        val displacement = dist - effectiveLinkDistance
                        val baseLinkMag = effectiveLink * displacement * distMul

                        val magA = baseLinkMag * biasA * (1f - connRepulsionMul)
                        val fxA = dx * invDist * magA + dx * invDist * repCompensation
                        val fyA = dy * invDist * magA + dy * invDist * repCompensation

                        val magB = baseLinkMag * biasB * (1f - connRepulsionMul)
                        val fxB = -dx * invDist * magB - dx * invDist * repCompensation
                        val fyB = -dy * invDist * magB - dy * invDist * repCompensation

                        if (dist < antiStickDist) {
                            val t = 1f - dist / antiStickDist
                            val stickFactor = t * t
                            val antiFx = dx * invDist * antiStickForce * stickFactor
                            val antiFy = dy * invDist * antiStickForce * stickFactor
                            tfx[a] -= antiFx
                            tfy[a] -= antiFy
                            tfx[b] += antiFx
                            tfy[b] += antiFy
                        }
                        if (a != draggedIdx) {
                            tfx[a] += fxA
                            tfy[a] += fyA
                        }
                        if (b != draggedIdx) {
                            tfx[b] += fxB
                            tfy[b] += fyB
                        }
                    }
                }
            }
        linkJobs.awaitAll()
    }

    // -----------------------------------------------------------------
    // syncData / setup
    // -----------------------------------------------------------------

    private fun ensureThreadBuffers(
        cores: Int,
        n: Int,
    ) {
        if (lastThreadCount != cores || threadForceX.isEmpty() || threadForceX[0].size < n) {
            threadForceX = Array(cores) { FloatArray(n.coerceAtLeast(MIN_ARRAY_CAPACITY)) }
            threadForceY = Array(cores) { FloatArray(n.coerceAtLeast(MIN_ARRAY_CAPACITY)) }
            lastThreadCount = cores
        }
    }

    private fun recomputeHubScale(exponent: Float) {
        if (hubScaleCache.size < nodeCount) {
            hubScaleCache = FloatArray(nodeCount.coerceAtLeast(MIN_ARRAY_CAPACITY))
        }
        for (i in 0 until nodeCount) {
            val d = degree[i].toFloat()
            hubScaleCache[i] = min(d.pow(exponent), config.maxHubScale)
        }
    }

    private fun syncData(
        graphNodes: List<GraphNode<Id, Data>>,
        coordinates: MutableMap<Id, Offset>,
        velocities: MutableMap<Id, Offset>,
        connections: Map<Id, List<Id>>,
        structureChanged: Boolean,
    ) {
        val n = graphNodes.size

        if (posX.size < n) {
            val newCap = n.coerceAtLeast(MIN_ARRAY_CAPACITY)
            posX = FloatArray(newCap)
            posY = FloatArray(newCap)
            velX = FloatArray(newCap)
            velY = FloatArray(newCap)
            forceX = FloatArray(newCap)
            forceY = FloatArray(newCap)
            smoothedVelX = FloatArray(newCap)
            smoothedVelY = FloatArray(newCap)
        }
        if (connectionsOffset.size < n + 1) {
            connectionsOffset = IntArray(n + 1)
        }
        if (degree.size < n) {
            degree = IntArray(n.coerceAtLeast(MIN_ARRAY_CAPACITY))
        }

        ids.clear()
        idToIndex.clear()
        nodeCount = n

        if (structureChanged) {
            lastHubExponent = Float.NaN
        }

        // "more nodes → slower decay" — symmetric around N=300
        alphaDecay =
            if (config.adaptiveDecayByNodeCount && nodeCount > 0) {
                (config.baseAlphaDecay * (DECAY_PIVOT_NODE_COUNT / nodeCount.coerceAtLeast(1)))
                    .coerceIn(config.minAlphaDecay, config.maxAlphaDecay)
            } else {
                config.baseAlphaDecay
            }

        for (i in 0 until n) {
            val node = graphNodes[i]
            ids.add(node.id)
            idToIndex[node.id] = i

            var pos = coordinates[node.id] ?: Offset.Zero
            if (pos == Offset.Zero) {
                // Nodes with no position get a one-time random spawn so they
                // don't all start coincident. Existing positions pass through
                // untouched — never any per-frame jitter.
                pos =
                    Offset(
                        (kotlin.random.Random.nextFloat() - MIDPOINT_FACTOR) * config.spawnSpread,
                        (kotlin.random.Random.nextFloat() - MIDPOINT_FACTOR) * config.spawnSpread,
                    )
                coordinates[node.id] = pos
            }
            posX[i] = pos.x
            posY[i] = pos.y
            val vel = velocities[node.id] ?: Offset.Zero
            velX[i] = vel.x
            velY[i] = vel.y
            // smoothedVel is engine-internal; do NOT seed from the host map.
            // It is updated each step and zeroed on sleep/freeze paths.
        }

        // Groups are synced AFTER ids/idToIndex/positions are populated, since
        // syncGroupsInternal resolves memberships by node id and the snapshot
        // it publishes is keyed by node INDEX in this exact order.
        syncGroupsInternal(graphNodes, n)

        // De-stack: nodes that loaded at the SAME position get a tiny one-time
        // deterministic offset so Barnes-Hut has a real direction to work with.
        // Runs only on a structural change — never per idle frame.
        if (structureChanged && n > 1) {
            deStackCoincidentNodes(coordinates)
        }

        // Count CSR entries
        var totalCsrConns = 0
        for (i in 0 until n) {
            val conns = connections[graphNodes[i].id] ?: continue
            for (cId in conns) {
                if (idToIndex.containsKey(cId)) totalCsrConns++
            }
        }
        if (connectionsFlat.size < totalCsrConns) {
            connectionsFlat = IntArray(totalCsrConns.coerceAtLeast(MIN_ARRAY_CAPACITY))
        }

        var write = 0
        for (i in 0 until n) {
            connectionsOffset[i] = write
            val conns = connections[graphNodes[i].id]
            var localDegree = 0
            if (conns != null) {
                for (cId in conns) {
                    val idx = idToIndex[cId]
                    if (idx != null && idx != i && idx < n) {
                        connectionsFlat[write++] = idx
                        localDegree++
                    }
                }
            }
            degree[i] = localDegree
        }
        connectionsOffset[n] = write

        // Deduplicated edge list (a < b)
        val edgeCap = (totalCsrConns / EDGE_COUNT_DIVISOR + MIN_ARRAY_CAPACITY).coerceAtLeast(MIN_ARRAY_CAPACITY)
        if (edgeA.size < edgeCap) {
            edgeA = IntArray(edgeCap)
            edgeB = IntArray(edgeCap)
        }
        var eWrite = 0
        for (i in 0 until n) {
            val from = connectionsOffset[i]
            val to = connectionsOffset[i + 1]
            for (k in from until to) {
                val j = connectionsFlat[k]
                if (j > i) {
                    if (eWrite >= edgeA.size) {
                        edgeA = edgeA.copyOf(edgeA.size * BUFFER_GROWTH_FACTOR)
                        edgeB = edgeB.copyOf(edgeB.size * BUFFER_GROWTH_FACTOR)
                    }
                    edgeA[eWrite] = i
                    edgeB[eWrite] = j
                    eWrite++
                }
            }
        }
        edgeCount = eWrite
    }

    /**
     * One-time fix for nodes that arrive stacked on an identical position.
     * Quantizes each position into a small map; any collision gets a tiny
     * golden-angle spiral offset. Cheap (O(n)) and runs only on structural
     * changes. Offsets are intentionally small so the layout still looks
     * "loaded at the same place" — the physics then spreads them properly.
     */
    private fun deStackCoincidentNodes(coordinates: MutableMap<Id, Offset>) {
        val seen = HashMap<Long, Int>()
        for (i in 0 until nodeCount) {
            // Quantize to ~0.5 units so near-identical positions collide too.
            val qx = (posX[i] * QUANTIZE_FACTOR).toInt()
            val qy = (posY[i] * QUANTIZE_FACTOR).toInt()
            val key = (qx.toLong() shl KEY_SHIFT_BITS) xor (qy.toLong() and LOWER_32_BIT_MASK)
            val prior = seen[key]
            if (prior == null) {
                seen[key] = i
            } else {
                // Spiral each duplicate outward by a stable golden-angle step.
                val rank = seen.size + i
                val ang = rank * GOLDEN_ANGLE_RADIANS
                val r = config.deStackRadius * (1f + (rank and SPIRAL_RANK_MASK) * SPIRAL_RADIUS_STEP)
                posX[i] += cos(ang) * r
                posY[i] += sin(ang) * r
                coordinates[ids[i]] = Offset(posX[i], posY[i])
            }
        }
    }

    // -----------------------------------------------------------------
    // Group data
    // -----------------------------------------------------------------

    private fun syncGroupsInternal(
        graphNodes: List<GraphNode<Id, Data>>,
        n: Int,
    ) {
        val index = pendingGroupIndex
        // Capture the identity ONCE at the top so a concurrent setGroupData call
        // can't make us publish the new snapshot stamped with a newer identity
        // than the data we actually read.
        val identityForThisSync = pendingGroupIndexIdentity

        if (index == null || !pendingGroupSettings.enabled || n == 0) {
            if (nodeGroupOffset.size < n + 1) {
                nodeGroupOffset = IntArray((n + 1).coerceAtLeast(1))
            }
            for (i in 0..n) nodeGroupOffset[i] = 0
            groupCount = 0
            groupEntryCount = 0
            groupIdToValue = emptyArray()
            lastGroupSignature = -1
            publishedGroupSnapshot.store(GroupSnapshot.EMPTY)
            // Stamp AFTER the snapshot store so a reader that sees the identity
            // is guaranteed to also see the matching snapshot.
            publishedGroupIndexIdentity = identityForThisSync
            return
        }

        // Group id -> dense engine index. Order follows model.defs so it's stable.
        val groupIds = index.groupIds
        val g = groupIds.size
        val groupIndexOf = HashMap<GroupId, Int>(g * GROUP_MAP_CAPACITY_FACTOR)
        for (gi in 0 until g) groupIndexOf[groupIds[gi]] = gi

        // Per-node resolved memberships (engine group index + weight), by node index.
        val perNodeIds = arrayOfNulls<IntArray>(n)
        val perNodeWeights = arrayOfNulls<FloatArray>(n)
        var totalEntries = 0

        for (i in 0 until n) {
            val memberships = index.membershipsOf(graphNodes[i].id)
            if (memberships.isEmpty()) continue
            // A membership whose groupId has no def is dropped (defensive).
            var count = 0
            for (m in memberships) if (groupIndexOf.containsKey(m.groupId)) count++
            if (count == 0) continue

            val gids = IntArray(count)
            val gws = FloatArray(count)
            var k = 0
            for (m in memberships) {
                val gi = groupIndexOf[m.groupId] ?: continue
                gids[k] = gi
                gws[k] = m.weight
                k++
            }
            perNodeIds[i] = gids
            perNodeWeights[i] = gws
            totalEntries += count
        }

        // Signature: group count, entry count, per-node membership shape, and
        // each group's id hash. Appearance (name/color) is NOT included — that
        // doesn't affect physics, so it must not trigger a reheat.
        var sig = g * SIGNATURE_PRIME + totalEntries
        for (i in 0 until n) sig = sig * HASH_MULTIPLIER + (perNodeIds[i]?.size ?: 0)
        for (gi in 0 until g) sig = sig * HASH_MULTIPLIER + groupIds[gi].raw.hashCode()
        val isFirstSync = lastGroupSignature == -1
        val groupsChanged = sig != lastGroupSignature
        lastGroupSignature = sig

        if (nodeGroupOffset.size < n + 1) {
            nodeGroupOffset = IntArray((n + 1).coerceAtLeast(MIN_ARRAY_CAPACITY))
        }
        if (nodeGroupId.size < totalEntries) {
            nodeGroupId = IntArray(totalEntries.coerceAtLeast(MIN_ARRAY_CAPACITY))
            nodeGroupWeight = FloatArray(totalEntries.coerceAtLeast(MIN_ARRAY_CAPACITY))
        }
        if (groupCentroidX.size < g) {
            val cap = g.coerceAtLeast(MIN_GROUP_CAPACITY)
            groupCentroidX = FloatArray(cap)
            groupCentroidY = FloatArray(cap)
            groupMemberCount = IntArray(cap)
            groupWeightSum = FloatArray(cap)
        }

        var w = 0
        for (i in 0 until n) {
            nodeGroupOffset[i] = w
            val gids = perNodeIds[i] ?: continue
            val gws = perNodeWeights[i]!!
            for (k in gids.indices) {
                nodeGroupId[w] = gids[k]
                nodeGroupWeight[w] = gws[k]
                w++
            }
        }
        nodeGroupOffset[n] = w
        groupCount = g
        groupEntryCount = w
        groupIdToValue = Array(g) { groupIds[it] }

        // ---- Atomic publish -------------------------------------------------
        // EVERY group array is now fully written and internally consistent:
        //   - nodeGroupOffset[0..n] partitions exactly w entries
        //   - nodeGroupId[0..w) / nodeGroupWeight[0..w) are filled
        //   - groupIdToValue has exactly g entries
        // Build an immutable, exact-sized copy and swap it in with a single
        // atomic store. Any concurrent snapshotGroupsForHulls() call observes
        // either the whole previous snapshot or this whole new one — never the
        // half-written live arrays. This is the fix for "edit one group ->
        // every hull blinks out for one frame".
        publishedGroupSnapshot.store(
            GroupSnapshot.build(
                groupCount = groupCount,
                nodeCount = n,
                groupIdToValue = groupIdToValue,
                liveNodeGroupOffset = nodeGroupOffset,
                liveNodeGroupId = nodeGroupId,
                liveNodeGroupWeight = nodeGroupWeight,
                entryCount = groupEntryCount,
            ),
        )
        // Stamp the identity AFTER the atomic snapshot store. Ordering matters:
        // hasSyncedGroupIndex reads the identity; if it sees the new value, the
        // snapshot store above has already happened (single physics coroutine,
        // and the @Volatile write here can't be reordered before the store).
        publishedGroupIndexIdentity = identityForThisSync

        if (groupsChanged && !isFirstSync && config.groupChangeReheatAlpha > 0f) {
            reheatInternal(config.groupChangeReheatAlpha)
        }
    }

    /**
     * Snapshot of (groupId, [(x,y)...]) for hull computation.
     *
     * Reads ONLY the atomically-published [GroupSnapshot] — never the engine's
     * live, mutable group arrays. The topology is therefore always whole and
     * self-consistent regardless of whether step()/syncGroupsInternal is
     * running concurrently on the physics coroutine.
     *
     * Positions (posX/posY) are still read live; that is intentional and safe
     * — see [GroupSnapshot.buildHullPoints]. The only thing that must not tear
     * is the membership topology, and that is what the snapshot guarantees.
     */
    override fun snapshotGroupsForHulls(): List<Pair<GroupId, FloatArray>> = publishedGroupSnapshot.load().buildHullPoints(posX, posY)

    override fun hasSyncedGroupIndex(groupIndexIdentity: Int): Boolean = publishedGroupIndexIdentity == groupIndexIdentity
}
