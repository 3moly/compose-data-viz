package com.moly3.dataviz.block.func

import androidx.compose.ui.unit.Density
import com.moly3.dataviz.block.model.ArcConnection
import com.moly3.dataviz.block.model.ConnectionConfig
import com.moly3.dataviz.block.model.DragAction
import com.moly3.dataviz.block.model.Shape
import com.moly3.dataviz.block.model.SelectedConnection
import com.moly3.dataviz.block.model.SaveableOffset

internal fun findConnection(
    shapes: List<Shape>,
    connections: List<ArcConnection>,
    dragAction: DragAction?,
    cursorPosition: SaveableOffset,
    centerOfScreen: SaveableOffset,
    userCoordinate: SaveableOffset,
    zoom: Float,
    config: ConnectionConfig
): SelectedConnection? {
    var startPointW: SaveableOffset? = null
    var endPointW: SaveableOffset? = null
    val foundConnection = connections.firstOrNull { connection ->
        val fromBox =
            shapes.firstOrNull { d -> d.id == connection.fromBox } ?: return@firstOrNull false
        val toBox = shapes.firstOrNull { d -> d.id == connection.toBox } ?: return@firstOrNull false

        // These transform world coordinates to screen coordinates
        val startPoint = makeSideOffset(
            dragAction = dragAction,
            userCoordinate = userCoordinate,
            boxSide = fromBox,
            zoom = zoom,
            side = connection.fromSide
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
            side = connection.toSide
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
            density = Density(1f, 1f),
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