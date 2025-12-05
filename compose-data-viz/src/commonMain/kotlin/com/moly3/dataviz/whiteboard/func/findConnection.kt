package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.unit.Density
import com.moly3.dataviz.core.whiteboard.model.ShapeConnection
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.SelectedConnection
import androidx.compose.ui.geometry.Offset

internal fun <Id> findConnection(
    shapes: List<Shape<Id>>,
    connections: List<ShapeConnection<Id>>,
    dragAction: DragAction<Id>?,
    cursorPosition: Offset,
    centerOfScreen: Offset,
    userCoordinate: Offset,
    zoom: Float,
    config: ConnectionConfig,
    roundToNearest: Int?,
    density: Float
): SelectedConnection<Id>? {
    var startPointW: Offset? = null
    var endPointW: Offset? = null
    val foundConnection = connections.lastOrNull { connection ->
        val fromBox =
            shapes.lastOrNull { d -> d.id == connection.fromBoxId } ?: return@lastOrNull false
        val toBox = shapes.lastOrNull { d -> d.id == connection.toBoxId } ?: return@lastOrNull false

        // These transform world coordinates to screen coordinates
        val startPoint = makeSideOffset(
            dragAction = dragAction,
            userCoordinate = userCoordinate,
            boxSide = fromBox,
            zoom = zoom,
            side = connection.fromSide,
            roundToNearest = roundToNearest
        )
        startPointW = makeSideWorldOffset(
            itemPosition = fromBox.position,
            boxSize = fromBox.size,
            side = connection.fromSide
        )
        val endPoint = makeSideOffset(
            dragAction = dragAction,
            userCoordinate = userCoordinate,
            boxSide = toBox,
            zoom = zoom,
            side = connection.toSide,
            roundToNearest = roundToNearest
        )
        endPointW = makeSideWorldOffset(
            itemPosition = toBox.position,
            boxSize = toBox.size,
            side = connection.toSide
        )

        // Ensure cursor is in the same coordinate space
        val cursorInScreenSpace = cursorPosition - centerOfScreen

        isNearArrow(
            cursor = cursorInScreenSpace,
            startPoint = startPoint,
            endPoint = endPoint,
            fromSide = connection.fromSide,
            toSide = connection.toSide,
            config = config,
            density = Density(density,1f),
            zoom = zoom
        )
    }

    if (startPointW == null || endPointW == null || foundConnection == null)
        return null
    return SelectedConnection(
        startPoint = startPointW,
        endPoint = endPointW,
        connection = foundConnection
    )
}