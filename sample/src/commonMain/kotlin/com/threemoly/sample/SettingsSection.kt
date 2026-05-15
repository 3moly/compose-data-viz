package com.threemoly.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.threemoly.sample.base.uikit.ObsSlider
import com.threemoly.sample.base.uikit.ObsText

// =====================================================================================
// Section — collapsible group of settings with a header
// =====================================================================================

@Composable
fun SettingsSection(
    title: String,
    initiallyExpanded: Boolean = false,
    accentColor: Color = Color(0xFF7E57C2),
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF7F7F7))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(Modifier.width(10.dp))
            ObsText(
                text = title,
                modifier = Modifier.weight(1f),
            )
            ObsText(
                text = if (expanded) "−" else "+",
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit  = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                content()
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// =====================================================================================
// SliderRow — label + value + slider in a tight, consistent layout
// =====================================================================================

@Composable
fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String = { it.toString() },
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ObsText(text = label, modifier = Modifier.weight(1f))
            ObsText(text = valueFormatter(value))
        }
        ObsSlider(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Composable
fun IntSliderRow(
    label: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
) {
    SliderRow(
        label = label,
        value = value.toFloat(),
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        valueFormatter = { it.toInt().toString() },
        onValueChange = { onValueChange(it.toInt()) },
    )
}

// =====================================================================================
// ToggleRow — label + on/off pill
// =====================================================================================

@Composable
fun ToggleRow(
    label: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onValueChange(!value) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ObsText(text = label, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 36.dp, height = 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (value) Color(0xFF7E57C2) else Color(0xFFBDBDBD))
                .padding(2.dp),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// =====================================================================================
// ColorRow — label + selectable color swatches
// =====================================================================================

private val DefaultPalette = listOf(
    Color(0xFF000000), Color(0xFF424242), Color(0xFF9E9E9E), Color(0xFFE0E0E0), Color(0xFFFFFFFF),
    Color(0xFFEF5350), Color(0xFFFF7043), Color(0xFFFFB300), Color(0xFFFFEE58),
    Color(0xFF66BB6A), Color(0xFF26A69A), Color(0xFF42A5F5), Color(0xFF5C6BC0),
    Color(0xFFAB47BC), Color(0xFFEC407A), Color(0xFF7E57C2),
)

@Composable
fun ColorRow(
    label: String,
    value: Color,
    palette: List<Color> = DefaultPalette,
    onValueChange: (Color) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ObsText(text = label, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(value)
                    .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            palette.forEach { swatch ->
                val selected = swatch == value
                Box(
                    Modifier
                        .size(if (selected) 22.dp else 18.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Color(0xFF7E57C2) else Color.Black.copy(alpha = 0.15f),
                            shape = CircleShape,
                        )
                        .clickable { onValueChange(swatch) }
                )
            }
        }
    }
}