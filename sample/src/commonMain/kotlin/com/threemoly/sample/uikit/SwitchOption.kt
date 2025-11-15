package com.threemoly.sample.uikit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SwitchOption(modifier: Modifier = Modifier, text: String, value: Boolean, onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ObsText(
            text = text,
            modifier = Modifier.weight(1f),
            softWrap = false
        )
        ObsSwitch(
            modifier = Modifier,
            checked = value,
            onCheckedChange = {
                onClick()
            })
    }
}