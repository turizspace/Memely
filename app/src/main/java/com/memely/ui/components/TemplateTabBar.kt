package com.memely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tab bar for template filtering: All Templates vs Favorites
 */
@Composable
fun TemplateTabBar(
    selectedTab: TemplateTab,
    onTabSelected: (TemplateTab) -> Unit,
    favoritesCount: Int = 0
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.padding(horizontal = 8.dp),
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.primary,
        divider = { TabRowDefaults.Divider(thickness = 2.dp) }
    ) {
        Tab(
            selected = selectedTab == TemplateTab.ALL,
            onClick = { onTabSelected(TemplateTab.ALL) },
            text = { Text("📋 All Templates") }
        )
        
        Tab(
            selected = selectedTab == TemplateTab.FAVORITES,
            onClick = { onTabSelected(TemplateTab.FAVORITES) },
            text = { Text("❤️ Favorites ($favoritesCount)") }
        )
    }
}

enum class TemplateTab {
    ALL, FAVORITES
}
