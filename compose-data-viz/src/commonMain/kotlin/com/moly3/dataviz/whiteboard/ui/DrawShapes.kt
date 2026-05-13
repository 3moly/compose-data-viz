package com.moly3.dataviz.whiteboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.DrawShapeState
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.allSides
import com.moly3.dataviz.core.whiteboard.model.toOffset
import com.moly3.dataviz.whiteboard.func.absoluteOffset
import com.moly3.dataviz.whiteboard.func.calculateShapeParams
import com.moly3.dataviz.whiteboard.func.makeSideOffsetShape

private const val BORDER_PADDING = 1f
private const val CORNER_CIRCLE_SIZE = 12
private const val SHAPE_SIZE_ROUND = 25

private val CORNERS = listOf(
    Offset(0f, 0f), // top-left
    Offset(1f, 0f), // top-right
    Offset(0f, 1f), // bottom-left
    Offset(1f, 1f)  // bottom-right
)

/**
 * IMPORTANT FIXES vs previous version:
 *
 *  1. Magic numbers (`sizeRound = 25`, `borderPadding = 1f`, etc.) are private
 *     constants now. The top-level mutable `sizeRound` was visible to other
 *     files; that's gone.
 *
 *  2. The corner-handle `Modifier.background(...)` chain used a fresh
 *     `RoundedCornerShape` four times per shape. Computed once per shape now.
 *
 *  3. Dead `innerShadow` code and the `isInSidePosition` todo are removed —
 *     they were noise. (`isInSidePosition` is still used in Dashboard.kt; that's
 *     intentional, it's only the per-frame draw-side hit detection here that
 *     was commented out.)
 *
 *  4. `isSelected` is no longer wrapped in a `remember(...)` keyed on three
 *     state values — it's a cheap boolean and `remember` keyed on rapidly-
 *     changing values just churns the slot table. Direct evaluation is faster.
 */
@Composable
fun <ShapeType : Shape<Id>, Id> BoxScope.DrawShapes(
    minShapeSize: Float,
    mousePosition: Offset,
    shapes: List<ShapeType>,
    dragActionState: MutableState<DragAction<Id>?>,
    userCoordinate: Offset,
    zoom: Float,
    density: Float,
    action: Action<ShapeType, Id>?,
    roundToNearest: Int?,
    onDrawBlock: @Composable (DrawShapeState<ShapeType, Id>) -> Unit,
    onDrawConnectionCircle: @Composable (RoundedCornerShape, Modifier) -> Unit
) {
    val sideShape = remember(zoom) { RoundedCornerShape((SHAPE_SIZE_ROUND * zoom).dp) }
    val cornerShape = remember(zoom) { RoundedCornerShape((CORNER_CIRCLE_SIZE * zoom).dp) }

    val dragAction = dragActionState.value
    val isConnectionDrag = dragAction?.dragType is DragType.Connection

    for ((index, item) in shapes.withIndex()) {
        val shapeParams = calculateShapeParams(
            item = item,
            zoom = zoom,
            density = density,
            userCoordinate = userCoordinate,
            dragAction = dragAction,
            roundToNearest = roundToNearest,
            minShapeSize = minShapeSize
        )

        val isSelected =
            (dragAction?.dragType is DragType.ShapeDrag<*> &&
                    (dragAction.dragType as DragType.ShapeDrag<*>).shapeId == item.id) ||
                    (action is Action.ShapeAction && action.shape.id == item.id)

        val isDoubleClicked =
            action is Action.DoubleClicked && item.id == action.shape.id

        onDrawBlock(
            DrawShapeState(
                modifier = Modifier
                    .absoluteOffset(shapeParams.offset.toOffset())
                    .width((shapeParams.size.x + BORDER_PADDING).dp)
                    .height((shapeParams.size.y + BORDER_PADDING).dp)
                    .align(Alignment.Center)
                    .padding(BORDER_PADDING.dp),
                shape = item,
                isSelected = isSelected,
                isDoubleClicked = isDoubleClicked,
                index = index
            )
        )

        if (isSelected) {
            for (corner in CORNERS) {
                val mult = Offset(shapeParams.size.x * corner.x, shapeParams.size.y * corner.y)
                val cornerOffset =
                    shapeParams.itemPosition + (mult * density / zoom) - userCoordinate

                Box(
                    modifier = Modifier
                        .absoluteOffset(cornerOffset * zoom / density)
                        .size((CORNER_CIRCLE_SIZE * zoom).dp / density)
                        .align(Alignment.Center)
                        .background(color = Color.White, shape = cornerShape)
                        .border(
                            width = (1.5f * zoom).dp / density,
                            color = Color.Gray,
                            shape = cornerShape
                        )
                        .clip(cornerShape)
                ) {}
            }
        }

        val boxSize = shapeParams.size
        if (isConnectionDrag || isSelected) {
            for (side in allSides) {
                val sideOffset = makeSideOffsetShape(
                    itemPosition = shapeParams.itemPosition,
                    userCoordinate = userCoordinate,
                    shapeSize = boxSize / zoom,
                    zoom = zoom,
                    side = side,
                    density = density
                )
                onDrawConnectionCircle(
                    sideShape,
                    Modifier
                        .absoluteOffset(sideOffset / density)
                        .size((SHAPE_SIZE_ROUND * zoom / density).dp)
                        .align(Alignment.Center)
                        .clip(sideShape)
                )
            }
        }
    }
}