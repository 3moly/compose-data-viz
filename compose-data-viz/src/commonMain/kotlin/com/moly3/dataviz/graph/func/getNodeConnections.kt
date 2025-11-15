package com.moly3.dataviz.graph.func

fun <Id> Map<Id, List<Id>>.getNodeConnections(nodeId: Id): List<Id> {
    return this[nodeId] ?: emptyList()
}