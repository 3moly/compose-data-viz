package com.threemoly.sample.graph

import kotlinx.serialization.Serializable

@Serializable
data class GraphSettingsConfig(
    val maxNodes: Int,
    val isShowDirectories: Boolean,
    val isRealFiles: Boolean,
    val isTags: Boolean,
    val isRows: Boolean,
    val isCollections: Boolean,
    val isOrphans: Boolean,
    val isGradations: Boolean
) {
    companion object {
        val Default = GraphSettingsConfig(
            isTags = true,
            isCollections = true,
            isRows = true,
            isRealFiles = false,
            isShowDirectories = true,

            isOrphans = false,
            maxNodes = 3000,
            isGradations = false
        )
    }
}