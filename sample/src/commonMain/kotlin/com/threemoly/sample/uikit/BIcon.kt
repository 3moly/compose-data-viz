package com.threemoly.sample.uikit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun BIcon(
    modifier: Modifier = Modifier,
    size: Int = 32,
    imageVector: ImageVector,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isEnabled) Color.Black else Color.Transparent)
            .let {
                if (isEnabled)
                    it.clickable { onClick() }
                else
                    it
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            imageVector,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}

//@Composable
//fun ObsIcon(
//    modifier: Modifier = Modifier,
//    painter: Painter,
//    isEnabled: Boolean = true,
//    onClick: () -> Unit
//) {
//    ThreeDBorderButton(
//        modifier = modifier
//            .width(28.dp)
//            .height(24.dp),
//        isEnabled = isEnabled,
//        cornerRadius = 4,
//        onClick = onClick
//    ) {
//        Image(
//            painter = painter,
//            contentDescription = null,
//            colorFilter = ColorFilter.tint(LocalAppTheme.current.colors.icon.copy(alpha = if (isEnabled) 1f else 0.5f))
//        )
//    }
//}