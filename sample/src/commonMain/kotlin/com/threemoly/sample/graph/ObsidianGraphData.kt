package com.threemoly.sample.graph

import kotlinx.serialization.Serializable

@Serializable
sealed class ObsidianGraphData {
    @Serializable
    data class Tag(val id: Long) : ObsidianGraphData()

    @Serializable
    data class Collection(val id: Long) : ObsidianGraphData()

    @Serializable
    data class CollectionRow(val id: Long, val collectionId: Long) : ObsidianGraphData()

    @Serializable
    data class File(val fullPath: String) : ObsidianGraphData()
}