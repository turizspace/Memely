package com.memely.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tab bar for personal media and favorites.
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
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("My Media")
                }
            }
        )
        
        Tab(
            selected = selectedTab == TemplateTab.FAVORITES,
            onClick = { onTabSelected(TemplateTab.FAVORITES) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Favorites ($favoritesCount)")
                }
            }
        )
    }
}

enum class TemplateTab {
    ALL, FAVORITES
}
