package com.threemoly.sample.base.func

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

fun makeBidirectional(connections: Map<String, List<String>>): Map<String, ImmutableList<String>> {
    val mutableGraph = connections.mapValues { it.value.toMutableList() }.toMutableMap()

    for ((node, connections) in connections) {
        for (target in connections) {
            mutableGraph.getOrPut(target) { mutableListOf() }.add(node)
        }
    }

    return mutableGraph.map { x -> x.key to x.value.toPersistentList() }.toMap()
}