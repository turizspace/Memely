package com.memely.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memely.ui.screens.BottomNavScreen
import com.memely.ui.tutorial.TutorialManager
import com.memely.ui.tutorial.tutorialTarget

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
                onClick = { 
                    // Check if tutorial is waiting for this tab click
                    val currentStep = TutorialManager.getCurrentStep()
                    val isActive = TutorialManager.isActive.value
                    
                    if (isActive && currentStep?.actionRequired == true) {
                        when {
                            currentStep.id == "explore_tab" && tab == BottomNavScreen.Explore -> {
                                TutorialManager.nextStep()
                            }
                            currentStep.id == "upload_tab" && tab == BottomNavScreen.Upload -> {
                                TutorialManager.nextStep()
                            }
                            currentStep.id == "profile_tab" && tab == BottomNavScreen.Profile -> {
                                TutorialManager.nextStep()
                            }
                            currentStep.id == "back_to_home" && tab == BottomNavScreen.Home -> {
                                TutorialManager.nextStep()
                            }
                        }
                    }
                    
                    onTabSelected(tab)
                },
                modifier = Modifier.tutorialTarget("tab_${tab.name.lowercase()}")
            )
        }
    }
}
