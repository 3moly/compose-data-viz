package com.threemoly.sample.uikit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BButton(
    modifier: Modifier = Modifier,
    text: String,
    backColor: Color = Color.Black,
    fontColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(backColor, shape = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        ObsText(
            text = text,
            style = TextStyle.Default,
            fontSize = 12.sp,
            color = fontColor
        )
    }
}