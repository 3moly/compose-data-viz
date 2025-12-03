package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.ShapeConnection
import com.moly3.dataviz.core.whiteboard.model.BoxSide
import com.moly3.dataviz.core.whiteboard.model.ConnectionConfig
import com.moly3.dataviz.core.whiteboard.model.DetectionType
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.PointerDetection
import com.moly3.dataviz.core.whiteboard.model.ResizeType
import com.moly3.dataviz.core.whiteboard.model.allSides
import com.moly3.dataviz.func.lastNotNullOfOrNull
import com.moly3.dataviz.whiteboard.func.toPointerType

fun <ShapeType : Shape<Id>, Id> calculatePointer(
    shapes: List<ShapeType>,
    mapCursor: Offset,
    connections: List<ShapeConnection<Id>>,
    zoom: Float,
    connectionConfig: ConnectionConfig,
    userCoordinate: Offset,
    centerOfScreen: Offset,
    cursorPosition: Offset,
    action: Action<ShapeType, Id>?,
    dragAction: DragAction<Id>?,
    sizeRound: Int,
    detectionPercent: Float,
    circleRadius: Float?,
    roundToNearest: Int?
): PointerDetection {

    val ses = getShapeGlobalDragType(
        mousePosition = mapCursor,
        shapes = shapes,
        action = action,
        sizeRound = sizeRound,
        circleRadius = circleRadius
    )

    val foundConnection = findConnection(
        shapes = shapes,
        connections = connections,
        dragAction = dragAction,
        cursorPosition = cursorPosition,
        centerOfScreen = centerOfScreen,
        userCoordinate = userCoordinate,
        zoom = zoom,
        config = connectionConfig,
        roundToNearest = roundToNearest
    )

    return if (ses != null) {
        when (val dragType = ses.first) {
            is DragType.Connection -> PointerDetection(PointerIcon.Hand, DetectionType.SideBox)
            is DragType.Resize -> PointerDetection(
                getPointerIcon(dragType.type.toPointerType()),
                DetectionType.ResizeBox
            )

            is DragType.ShapeDrag -> PointerDetection(PointerIcon.Hand, DetectionType.SideBox)
        }
    } else if (foundConnection != null) {
        PointerDetection(PointerIcon.Hand, DetectionType.Arrow)
    } else
        PointerDetection(PointerIcon.Default, null)
}