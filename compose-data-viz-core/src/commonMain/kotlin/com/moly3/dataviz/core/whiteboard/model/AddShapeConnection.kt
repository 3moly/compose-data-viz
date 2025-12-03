package com.moly3.dataviz.core.whiteboard.model

data class AddShapeConnection<Id>(
    val fromBoxId: Id,
    val toBoxId: Id,
    val fromSide: BoxSide,
    val toSide: BoxSide,
)