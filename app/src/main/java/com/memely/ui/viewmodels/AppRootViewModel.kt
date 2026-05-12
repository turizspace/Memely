package com.memely.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memely.data.repositories.SessionRepository
import com.memely.data.repositories.ThemeRepository
import com.memely.ui.theme.ThemeManager
import com.memely.ui.theme.ThemePreference
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class AppRootUiState(
    val isLoggedIn: Boolean = false,
    val currentTheme: ThemePreference = ThemeManager.THEME_LIGHT
)

class AppRootViewModel(
    private val sessionRepository: SessionRepository,
    private val themeRepository: ThemeRepository
) : ViewModel() {
    val uiState: StateFlow<AppRootUiState> = combine(
        sessionRepository.sessionState,
        themeRepository.theme
    ) { sessionState, currentTheme ->
        AppRootUiState(
            isLoggedIn = sessionState.isLoggedIn,
            currentTheme = currentTheme
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppRootUiState(
            isLoggedIn = sessionRepository.sessionState.value.isLoggedIn,
            currentTheme = themeRepository.theme.value
        )
    )

    fun refreshAuth() {
        sessionRepository.refresh()
    }

    fun logout() {
        sessionRepository.logout()
    }

    fun updateTheme(themePreference: ThemePreference) {
        themeRepository.setTheme(themePreference)
    }
}
