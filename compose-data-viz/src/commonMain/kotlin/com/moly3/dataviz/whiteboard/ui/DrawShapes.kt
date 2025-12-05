package com.moly3.dataviz.whiteboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.core.whiteboard.model.Action
import com.moly3.dataviz.core.whiteboard.model.DragAction
import com.moly3.dataviz.core.whiteboard.model.DragType
import com.moly3.dataviz.core.whiteboard.model.DrawShapeState
import com.moly3.dataviz.core.whiteboard.model.Shape
import com.moly3.dataviz.core.whiteboard.model.allSides
import com.moly3.dataviz.whiteboard.func.absoluteOffset
import com.moly3.dataviz.whiteboard.func.calculateShapeParams
import com.moly3.dataviz.whiteboard.func.makeSideOffsetShape


val borderPadding = 1f
val corners = listOf(
    Offset(0f, 0f), // Top-left
    Offset(1f, 0f),  // Top-right
    Offset(0f, 1f),  // Bottom-left
    Offset(1f, 1f)    // Bottom-right
)
val sizeRound = 25

@Composable
fun <ShapeType : Shape<Id>, Id> BoxScope.DrawShapes(
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
    val shape = RoundedCornerShape((sizeRound * zoom).dp)

    val isConnectionDrag = dragActionState.value?.dragType is DragType.Connection
    for ((index, item) in shapes.withIndex()) {
        val shapeParams = calculateShapeParams(
            item = item,
            zoom = zoom,
            density = density,
            userCoordinate = userCoordinate,
            dragAction = dragActionState.value,
            roundToNearest = roundToNearest
        )
        val isSelected = remember(item.id, dragActionState.value, action) {
            val dragAction = dragActionState.value
            dragAction != null &&
                    dragAction.dragType is DragType.ShapeDrag &&
                    (dragAction.dragType as DragType.ShapeDrag).shapeId == item.id ||
                    action is Action.ShapeAction &&
                    action.shape.id == item.id
        }

        onDrawBlock(
            DrawShapeState(
                modifier = Modifier
                    .absoluteOffset(shapeParams.offset.x, shapeParams.offset.y)
                    .width((shapeParams.size.x + borderPadding).dp)
                    .height((shapeParams.size.y + borderPadding).dp)
                    .align(Alignment.Center)
                    .padding(borderPadding.dp),
                shape = item,
                isSelected = isSelected,
                isDoubleClicked = action is Action.DoubleClicked && item.id == action.shape.id,
                index = index
            )
        )

        if (isSelected) {
            val cornerCircleSize = 12

            for (corner in corners) {
                val mutli = Offset(shapeParams.size.x * corner.x, shapeParams.size.y * corner.y)
                val cornerOffset =
                    shapeParams.itemPosition + (mutli * density / zoom) - userCoordinate

                Box(
                    modifier = Modifier
                        .absoluteOffset(cornerOffset * zoom / density)
                        .size((cornerCircleSize * zoom).dp / density)
                        .align(Alignment.Center)
                        .background(
                            color = Color.White,
                            RoundedCornerShape((cornerCircleSize * zoom).dp)
                        )
                        .border(
                            width = (1.5f * zoom).dp / density,
                            color = Color.Gray,
                            shape = RoundedCornerShape((cornerCircleSize * zoom).dp)
                        )
                        .clip(RoundedCornerShape((cornerCircleSize * zoom).dp)),
                ) {}
            }
        }
        val boxSize = shapeParams.size

        for (side in allSides) {
            val sideOffset = makeSideOffsetShape(
                itemPosition = shapeParams.itemPosition,
                userCoordinate = userCoordinate,
                shapeSize = boxSize / zoom,
                zoom = zoom,
                side = side,
                density = density
            )
//          todo  val isInSide = isInSidePosition(
//                mousePosition = mousePosition,
//                itemPosition = shapeParams.itemPosition,
//                boxSize = boxSize / zoom,
//                side = side,
//                radius = sizeRound / 2f
//            )
            if (isConnectionDrag || isSelected) {
                onDrawConnectionCircle(
                    shape,
                    Modifier
                        .absoluteOffset(sideOffset / density)
                        .size((sizeRound * zoom / density).dp)
                        .align(Alignment.Center)
                        .clip(shape)
                )
//                Box(
//                    modifier =
//                    . innerShadow (shape) {
//                    this.color = Color.Red
//                    radius = 4f * zoom
//                },
//                ) {}
            }
        }
    }
}