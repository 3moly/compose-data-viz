package com.threemoly.sample.base.graph


sealed class ObsidianGraphData {
    data class Tag(val id: Long) : ObsidianGraphData()
    data class Collection(val id: Long) : ObsidianGraphData()
    data class CollectionRow(val id: Long, val collectionId: Long) : ObsidianGraphData()
    data class File(val fullPath: String) : ObsidianGraphData()
}