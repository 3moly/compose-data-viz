package com.threemoly.sample.base.uikit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

@Composable
fun ButtonIcon(
    modifier: Modifier,
    painter: Painter,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(color, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painter, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}