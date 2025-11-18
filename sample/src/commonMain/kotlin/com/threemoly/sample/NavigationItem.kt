package com.threemoly.sample

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun NavigationItem(page: Page, selected: Page, onSelect: (Page) -> Unit) {
    NavigationRailItem(
        selected = selected.key == page.key,
        onClick = { onSelect(page) },
        icon = { Icon(page.icon, "") },
        label = { Text(page.text.replaceFirstChar { it.uppercase() }) }
    )
}