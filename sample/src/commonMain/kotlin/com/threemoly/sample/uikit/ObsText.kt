package com.threemoly.sample.uikit

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
fun ObsText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
) {
    BasicText(
        modifier = modifier,
        text = text,
        style = style.merge(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight
        ),
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        minLines = minLines
    )
}

@Composable
fun ObsText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    textAlign: TextAlign = TextAlign.Unspecified
) {
    BasicText(
        modifier = modifier,
        text = text,
        style = style.merge(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign
        ),
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        minLines = minLines
    )
}