package com.moly3.dataviz.block.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
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
import com.moly3.dataviz.block.model.Action
import com.moly3.dataviz.block.model.DragAction
import com.moly3.dataviz.block.model.DragType
import com.moly3.dataviz.block.model.DrawShapeState
import com.moly3.dataviz.block.model.Shape
import androidx.compose.ui.geometry.Offset
import com.moly3.dataviz.block.model.allSides

@Composable
fun BoxScope.DrawShapes(
    mousePosition: Offset,
    shapes: List<Shape>,
    dragActionState: MutableState<DragAction?>,
    userCoordinate: Offset,
    zoom: Float,
    density: Float,
    action: Action?,
    sideCircleColor: Color,
    roundToNearest: Int?,
    onDrawBlock: @Composable (DrawShapeState) -> Unit,
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
                    dragAction.dragType is DragType.Shape &&
                    dragAction.dragType.shapeId == item.id ||
                    action is Action.Shape &&
                    action.shape.id == item.id
        }
        Box(
            modifier = Modifier
                .absoluteOffset(shapeParams.offset.x, shapeParams.offset.y)
                .width(shapeParams.itemWidth)
                .height(shapeParams.itemHeight)
                .align(Alignment.Center)
        ) {
            onDrawBlock(
                DrawShapeState(
                    shape = item,
                    isSelected = isSelected,
                    isDoubleClicked = action is Action.DoubleClicked && item.id == action.shape.id
                )
            )
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