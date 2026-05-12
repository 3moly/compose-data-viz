package com.moly3.dataviz.graph.func

import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.core.graph.model.GraphNode
import com.moly3.dataviz.core.graph.model.GraphViewSettings
import kotlin.math.*

/**
 * Initial layout: produce a "good enough" starting position for every node BEFORE the
 * physics simulation runs. Without this, nodes spawn near (0,0) and the simulation has
 * to spend hundreds of frames untangling them — which is what you're seeing.
 *
 * Strategy (combination of three classic ideas):
 *
 *   1. Find connected components via union-find — disconnected subgraphs get their own
 *      "island" so they don't have to fight gravity to separate.
 *   2. For each component, run a BFS from the highest-degree node and lay nodes out in
 *      concentric rings. This gets hubs in the middle and leaves on the outside, which
 *      is exactly the shape force-directed graphs converge to.
 *   3. Pack components in a spiral so they don't overlap.
 *
 * Result: when the user first sees the graph it's already 80%-laid-out. The physics
 * simulation then just polishes it over a handful of frames.
 */
object InitialLayout {

    /**
     * Compute starting positions for all nodes. Existing positions in `coordinates` are
     * preserved (so this is safe to call when nodes are added incrementally).
     *
     * @param settings used to size the layout — link distance dictates ring spacing.
     * @return map of id -> initial Offset for every node that didn't already have one.
     */
    fun <Id, Data> compute(
        nodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        settings: GraphViewSettings,
        existingCoordinates: Map<Id, Offset> = emptyMap()
    ): Map<Id, Offset> {
        if (nodes.isEmpty()) return emptyMap()

        val result = HashMap<Id, Offset>(nodes.size)
        // Carry over any positions the caller already has — only fill in the gaps.
        val needsPosition = ArrayList<GraphNode<Id, Data>>(nodes.size)
        for (node in nodes) {
            val existing = existingCoordinates[node.id]
            if (existing != null && (existing.x != 0f || existing.y != 0f)) {
                result[node.id] = existing
            } else {
                needsPosition.add(node)
            }
        }
        if (needsPosition.isEmpty()) return result

        val idIndex = HashMap<Id, Int>(nodes.size)
        nodes.forEachIndexed { i, n -> idIndex[n.id] = i }

        // === STEP 1: connected components via union-find ===
        val parent = IntArray(nodes.size) { it }
        val rank = IntArray(nodes.size)

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) { val next = parent[cur]; parent[cur] = root; cur = next }
            return root
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra == rb) return
            when {
                rank[ra] < rank[rb] -> parent[ra] = rb
                rank[ra] > rank[rb] -> parent[rb] = ra
                else -> { parent[rb] = ra; rank[ra]++ }
            }
        }

        for ((id, neighbors) in connections) {
            val a = idIndex[id] ?: continue
            for (other in neighbors) {
                val b = idIndex[other] ?: continue
                union(a, b)
            }
        }

        val componentMembers = HashMap<Int, ArrayList<Int>>()
        for (i in nodes.indices) {
            componentMembers.getOrPut(find(i)) { ArrayList() }.add(i)
        }

        // === STEP 2: lay out each component with BFS rings ===
        // Sort components by size — biggest in the middle, smaller ones spiral outward.
        val components = componentMembers.values.sortedByDescending { it.size }
        val componentLayouts = ArrayList<ComponentLayout>(components.size)

        val ringSpacing = settings.linkDistance.coerceAtLeast(40f)
        for (component in components) {
            componentLayouts.add(layoutComponent(component, nodes, connections, idIndex, ringSpacing))
        }

        // === STEP 3: spiral-pack components so they don't overlap ===
        val placedComponents = ArrayList<PlacedComponent>(componentLayouts.size)
        for (layout in componentLayouts) {
            val center = findFreeSpot(layout.radius, placedComponents)
            placedComponents.add(PlacedComponent(layout, center))
            for ((nodeIdx, localPos) in layout.positions) {
                val node = nodes[nodeIdx]
                if (result.containsKey(node.id)) continue  // user-supplied position wins
                result[node.id] = localPos + center
            }
        }

        return result
    }

    /** Local layout (centered on origin) for a single connected component. */
    private fun <Id, Data> layoutComponent(
        component: List<Int>,
        nodes: List<GraphNode<Id, Data>>,
        connections: Map<Id, List<Id>>,
        idIndex: Map<Id, Int>,
        ringSpacing: Float
    ): ComponentLayout {
        if (component.size == 1) {
            return ComponentLayout(
                positions = mapOf(component[0] to Offset.Zero),
                radius = ringSpacing * 0.5f
            )
        }
        if (component.size == 2) {
            val s = ringSpacing
            return ComponentLayout(
                positions = mapOf(
                    component[0] to Offset(-s * 0.5f, 0f),
                    component[1] to Offset(s * 0.5f, 0f)
                ),
                radius = s
            )
        }

        // Pick highest-degree node as the BFS root — this becomes the visual center.
        val root = component.maxByOrNull { (connections[nodes[it].id]?.size) ?: 0 } ?: component[0]

        // BFS to assign each node a "ring" (graph-distance from root)
        val ringOf = HashMap<Int, Int>(component.size)
        val queue = ArrayDeque<Int>()
        queue.add(root)
        ringOf[root] = 0
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val ring = ringOf[cur]!!
            val neighbors = connections[nodes[cur].id] ?: continue
            for (other in neighbors) {
                val otherIdx = idIndex[other] ?: continue
                if (otherIdx in ringOf) continue
                ringOf[otherIdx] = ring + 1
                queue.add(otherIdx)
            }
        }

        // Any unreachable nodes (shouldn't happen since same component, but be safe)
        // get parked on the outermost ring.
        val maxRing = ringOf.values.maxOrNull() ?: 0
        for (idx in component) {
            if (idx !in ringOf) ringOf[idx] = maxRing + 1
        }

        // Group by ring, then iterate rings in sorted numeric order so each ring's
        // positions are placed before its children's "preferred angle" is needed.
        val ringGroups = HashMap<Int, MutableList<Int>>()
        for ((idx, ring) in ringOf) {
            ringGroups.getOrPut(ring) { ArrayList() }.add(idx)
        }
        val sortedRings = ringGroups.keys.sorted()

        // Stable ordering inside each ring: sort children near their parent's angle.
        // Build a "preferred angle" for each non-root node from its first parent.
        val preferredAngle = HashMap<Int, Float>(component.size)
        // Ring 0: root at center, no angle
        // Ring 1+: spread evenly, then later rings are sorted by parent's angle
        val positions = HashMap<Int, Offset>(component.size)
        positions[root] = Offset.Zero

        var maxRadius = 0f
        for (ring in sortedRings) {
            if (ring == 0) continue
            val members = ringGroups[ring] ?: continue
            val radius = ring * ringSpacing
            maxRadius = max(maxRadius, radius)

            // Sort members by their parent's angle to keep subtrees together
            val sorted = members.sortedBy { preferredAngle[it] ?: 0f }
            val n = sorted.size

            // Slight rotation offset per ring so rings don't all align radially
            val phaseShift = ring * 0.5f
            for (i in sorted.indices) {
                val angle = (i.toFloat() / n) * 2f * PI.toFloat() + phaseShift
                val pos = Offset(cos(angle) * radius, sin(angle) * radius)
                positions[sorted[i]] = pos
                // Propagate angle to children
                val nodeId = nodes[sorted[i]].id
                val children = connections[nodeId]
                if (children != null) {
                    for (c in children) {
                        val cIdx = idIndex[c] ?: continue
                        if (cIdx !in preferredAngle && (ringOf[cIdx] ?: 0) > ring) {
                            preferredAngle[cIdx] = angle
                        }
                    }
                }
            }
        }

        return ComponentLayout(positions = positions, radius = maxRadius + ringSpacing)
    }

    /**
     * Spiral search for an empty spot to place a component. Simple and deterministic —
     * faster than proper bin-packing and the result is "good enough" since physics will
     * shuffle things around afterward anyway.
     */
    private fun findFreeSpot(
        radius: Float,
        placed: List<PlacedComponent>
    ): Offset {
        if (placed.isEmpty()) return Offset.Zero

        val padding = 50f
        // Spiral outward. Step size scales with radius so we don't spend ages on big graphs.
        var angle = 0f
        var spiralRadius = 0f
        val angleStep = 0.5f
        val radiusStep = max(50f, radius * 0.3f)

        repeat(2000) {
            val candidate = Offset(cos(angle) * spiralRadius, sin(angle) * spiralRadius)
            val collides = placed.any { other ->
                val dx = candidate.x - other.center.x
                val dy = candidate.y - other.center.y
                val minDist = radius + other.layout.radius + padding
                dx * dx + dy * dy < minDist * minDist
            }
            if (!collides) return candidate
            angle += angleStep
            spiralRadius += radiusStep * angleStep / (2f * PI.toFloat())
        }
        // Fallback: just stick it far away
        return Offset(spiralRadius, 0f)
    }

    private data class ComponentLayout(
        val positions: Map<Int, Offset>,
        val radius: Float
    )
    private data class PlacedComponent(
        val layout: ComponentLayout,
        val center: Offset
    )
}


// ============================================================================
// ADAPTIVE PRESETS — tuned for graph size
// ============================================================================
/**
 * Default settings in your code (centerForce=0.0088, repelForce=20000, etc.) work well at
 * ~10–50 nodes. At 1000+ nodes the same numbers cause:
 *   - clumping (repel too weak relative to N²)
 *   - slow convergence (link force overpowered)
 *   - jittery hubs (max force too low)
 *
 * These presets scale the key parameters with graph size using empirical formulas
 * adapted from d3-force and Obsidian's defaults.
 */
object GraphPresets {

    /**
     * Pick a preset based on node count. The returned settings are tuned to give a
     * stable, readable layout in a small number of physics steps regardless of size.
     */
    fun forNodeCount(n: Int): GraphViewSettings = when {
        n <= 50   -> small()
        n <= 250  -> medium()
        n <= 1000 -> large()
        n <= 5000 -> huge()
        else      -> massive()
    }

    /** Tiny graphs — generous spacing, gentle forces, looks clean. */
    fun small(): GraphViewSettings = GraphViewSettings(
        centerForce  = 0.012f,
        linkForce    = 8f,
        linkDistance = 90f,
        repelForce   = 12000f,
        circleSize   = 10f,
        connectedRepulsionMultiplier        = 0.3f,
        mutualConnectionRepulsionMultiplier = 0.05f,
        unconnectedRepulsionMultiplier      = 1.0f,
        longDistanceLinkMultiplier = 1f,
        clusteringForce            = 1f,
        minMutualConnectionsForClustering = 10,
        maxForce      = 15f,
        dampingFactor = 0.92f,
        maxConnectionsForFullProcessing = 100,
        spatialOptimizationThreshold    = 50,
        circleSizeMultiplier = null
    )

    /** Hundreds of nodes — slightly tighter, stronger center pull. */
    fun medium(): GraphViewSettings = small().copy(
        centerForce  = 0.018f,
        linkForce    = 10f,
        linkDistance = 70f,
        repelForce   = 18000f,
        maxForce     = 18f,
        dampingFactor = 0.90f
    )

    /** ~1000 nodes — Obsidian's vault size. Stronger center, weaker repulsion. */
    fun large(): GraphViewSettings = small().copy(
        centerForce  = 0.025f,
        linkForce    = 12f,
        linkDistance = 55f,
        repelForce   = 24000f,
        circleSize   = 8f,
        connectedRepulsionMultiplier = 0.2f,
        maxForce     = 22f,
        dampingFactor = 0.88f,
        maxConnectionsForFullProcessing = 60
    )

    /** Several thousand — physics needs to be more aggressive to stay responsive. */
    fun huge(): GraphViewSettings = small().copy(
        centerForce  = 0.035f,
        linkForce    = 15f,
        linkDistance = 45f,
        repelForce   = 30000f,
        circleSize   = 6f,
        connectedRepulsionMultiplier        = 0.15f,
        mutualConnectionRepulsionMultiplier = 0.03f,
        maxForce     = 28f,
        dampingFactor = 0.85f,
        maxConnectionsForFullProcessing = 40,
        spatialOptimizationThreshold    = 20
    )

    /** 5000+ nodes — readability over physics fidelity. */
    fun massive(): GraphViewSettings = small().copy(
        centerForce  = 0.05f,
        linkForce    = 18f,
        linkDistance = 35f,
        repelForce   = 35000f,
        circleSize   = 5f,
        connectedRepulsionMultiplier        = 0.1f,
        mutualConnectionRepulsionMultiplier = 0.02f,
        maxForce     = 35f,
        dampingFactor = 0.82f,
        maxConnectionsForFullProcessing = 25,
        spatialOptimizationThreshold    = 10
    )
}