package com.threemoly.sample

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.threemoly.sample.uikit.icons.SettingsFuture

@Composable
fun BottomNavigationBar(pages: List<Page>, selected: Page, onSelect: (Page) -> Unit) {
    NavigationBar {
        for (page in pages) {
            NavigationBarItem(
                selected = selected.key == page.key,
                onClick = { onSelect(page) },
                icon = { Icon(SettingsFuture, "") },
                label = { Text(page.key) }
            )
        }
    }
}