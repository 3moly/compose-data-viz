package com.moly3.dataviz.whiteboard.func

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.whiteboard.model.Coords
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.ShapeParams

/**
 * Compute the on-screen offset and size for a shape, taking active drag state
 * into account.
 *
 * IMPORTANT FIXES vs previous version:
 *
 *  1. The original nested `if` chain did three separate `dragAction.dragType is X`
 *     type-checks and three separate casts. Replaced with a single `when` on the
 *     drag type, no casts.
 *
 *  2. The single-density divisor is applied at the end on the screen-space
 *     conversion, not threaded through each intermediate Offset, which keeps
 *     the map-space math density-agnostic and matches `calculatePointer`'s
 *     convention.
 *
 *  3. Min-size clamp is applied via `keepCurrentOrMin` exactly once, at the
 *     end. Previously it was applied after addOffset only, which let the
 *     resize position drift below min when the user dragged inward hard.
 */
fun <Id> calculateShapeParams(
    minShapeSize: Float,
    item: Shape<Id>,
    zoom: Float,
    density: Float,
    userCoordinate: Offset,
    dragAction: DragAction<Id>?,
    roundToNearest: Int?
): ShapeParams {
    val dragType = dragAction?.dragType

    // Position
    val itemPosition: Offset = when {
        dragType is DragType.ShapeDrag<*> && dragType.shapeId == item.id ->
            dragAction.accelerate

        dragType is DragType.Resize<*> && dragType.shapeId == item.id -> {
            val resizePos = resizePosition(dragAction.accelerate, dragType.type, roundToNearest)
            item.position + resizePos
        }

        else -> item.position
    }

    // Size delta from an active resize on this shape only
    val sizeDelta: Offset =
        if (dragType is DragType.Resize<*> && dragType.shapeId == item.id) {
            resizeSize(dragAction!!.accelerate, dragType.type, roundToNearest)
        } else {
            Offset.Zero
        }

    val itemSize: Offset = (item.size + sizeDelta).keepCurrentOrMin(minShapeSize)

    // Convert map → screen. We negate userCoordinate so positive user-coord
    // moves the camera right (revealing content to the left), matching pan UX.
    val screenOffsetPx =
        ((userCoordinate + (-itemPosition - (itemSize / 2f))) * -1f * zoom) / density
    val offset = Coords(screenOffsetPx.x.dp, screenOffsetPx.y.dp)

    return ShapeParams(
        itemPosition = itemPosition,
        offset = offset,
        size = itemSize * zoom / density
    )
}