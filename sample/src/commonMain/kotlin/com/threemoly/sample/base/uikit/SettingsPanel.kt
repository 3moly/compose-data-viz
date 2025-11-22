package com.threemoly.sample.base.uikit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.threemoly.sample.base.uikit.icons.Settings

@Composable
fun BoxScope.SettingsPanel(
    backgroundColor: Color,
    isShowSettings: Boolean,
    onSetSettings: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val settingsWidth by animateDpAsState(if (isShowSettings) 130.dp else 48.dp)
    Column(
        modifier = Modifier
            .padding(16.dp)
            .align(Alignment.TopEnd)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier.width(settingsWidth).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            BIcon(imageVector = Settings, onClick = {
                onSetSettings(!isShowSettings)
            })
            AnimatedVisibility(isShowSettings) {
                Column {
                    content()
                }
            }
        }
    }
}