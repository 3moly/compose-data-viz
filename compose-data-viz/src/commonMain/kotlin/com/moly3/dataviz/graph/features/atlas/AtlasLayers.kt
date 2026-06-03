package com.moly3.dataviz.graph.features.atlas

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class AtlasLayers(
    val layers: ImmutableList<AtlasState>,
) {
    fun resolve(key: String): AtlasLookup? {
        for (i in layers.indices) {
            val idx = layers[i].indexMap[key]
            if (idx != null) return AtlasLookup(layerIndex = i, tileIndex = idx)
        }
        return null
    }

    val isEmpty: Boolean get() = layers.isEmpty()

    val combinedVersion: Long get() =
        layers.fold(0L) { acc, a ->
            acc * HASH_PRIME_MULTIPLIER + a.version
        }

    companion object {
        private const val HASH_PRIME_MULTIPLIER = 31
        val EMPTY = AtlasLayers(persistentListOf())
    }
}
