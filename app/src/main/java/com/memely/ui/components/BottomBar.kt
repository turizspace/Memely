package com.memely.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memely.ui.screens.BottomNavScreen

@Composable
fun BottomBar(
    tabs: List<BottomNavScreen>,
    selectedTab: BottomNavScreen,
    onTabSelected: (BottomNavScreen) -> Unit
) {
    BottomNavigation(
        backgroundColor = MaterialTheme.colors.surface,      // white in light, dark in dark mode
        contentColor = MaterialTheme.colors.onSurface,       // text/icon color auto-adapts
        elevation = 4.dp,
        modifier = Modifier.height(56.dp)
    ) {
        tabs.forEach { tab ->
            BottomNavigationItem(
                icon = { Icon(tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title) },
                selected = tab == selectedTab,
                selectedContentColor = MaterialTheme.colors.primary, // highlighted tab
                unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), // unselected tabs
                onClick = { onTabSelected(tab) }
            )
        }
    }
}
