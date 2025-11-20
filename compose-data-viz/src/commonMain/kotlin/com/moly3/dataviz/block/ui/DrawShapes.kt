package com.moly3.dataviz.block.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moly3.dataviz.block.func.absoluteOffset
import com.moly3.dataviz.block.func.calculateShapeParams
import com.moly3.dataviz.block.func.isInSidePosition
import com.moly3.dataviz.block.func.makeSideOffset
import com.moly3.dataviz.core.block.model.Action
import com.moly3.dataviz.core.block.model.DragAction
import com.moly3.dataviz.core.block.model.DragType
import com.moly3.dataviz.core.block.model.DrawShapeState
import com.moly3.dataviz.core.block.model.Shape
import com.moly3.dataviz.core.block.model.allSides
import androidx.compose.ui.geometry.Offset


val borderPadding = 1.dp

@Composable
fun <ShapeType : Shape> BoxScope.DrawShapes(
    mousePosition: Offset,
    shapes: List<ShapeType>,
    dragActionState: MutableState<DragAction?>,
    userCoordinate: Offset,
    zoom: Float,
    density: Float,
    action: Action?,
    sideCircleColor: Color,
    selectedShapeBorderColor: Color,
    roundToNearest: Int?,
    onDrawBlock: @Composable (DrawShapeState<ShapeType>) -> Unit,
) {
    for (item in shapes) {
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
                    .width(shapeParams.itemWidth + borderPadding)
                    .height(shapeParams.itemHeight + borderPadding)
                    .align(Alignment.Center)
                    .let {
                        if (isSelected) {
                            it.border(borderPadding, selectedShapeBorderColor)
                        } else {
                            it
                        }
                    }.padding(borderPadding),
                shape = item,
                isSelected = isSelected,
                isDoubleClicked = action is Action.DoubleClicked && item.id == action.shape.id
            )
        )

        // Draw corner circles
        if (isSelected) {
            val cornerCircleSize = 12
            val corners = listOf(
                Offset(0f, 0f), // Top-left
                Offset(1f * density, 0f),  // Top-right
                Offset(0f, 1f * density),  // Bottom-left
                Offset(1f * density, 1f * density)    // Bottom-right
            )
            for (corner in corners) {
                val cornerOffset = Offset(
                    x = shapeParams.itemPosition.x + (shapeParams.itemWidth.value * corner.x / zoom),
                    y = shapeParams.itemPosition.y + (shapeParams.itemHeight.value * corner.y / zoom)
                ) - userCoordinate

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

        val sizeRound = 25
        for (side in allSides) {
            val sideOffset = makeSideOffset(
                itemPosition = shapeParams.itemPosition,
                userCoordinate = userCoordinate,
                boxSize = item.size,
                zoom = zoom,
                side = side
            )
            val isInSide = isInSidePosition(
                mousePosition = mousePosition,
                itemPosition = shapeParams.itemPosition,
                boxSize = item.size,
                side = side,
                radius = sizeRound / 2f
            )
            if (isInSide) {
                Box(
                    modifier = Modifier
                        .absoluteOffset(sideOffset / density)
                        .size((sizeRound * zoom).dp / density)
                        .align(Alignment.Center)
                        .background(
                            color = sideCircleColor,
                            RoundedCornerShape((sizeRound * zoom).dp)
                        )
                        .clip(RoundedCornerShape((sizeRound * zoom).dp)),
                ) {}
            }
        }
    }
}