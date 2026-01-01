package com.memely.ui.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages scroll state for the template grid to preserve user's position
 * when navigating away and back to the home feed.
 */
object TemplateGridScrollState {
    private val _scrollIndex = MutableStateFlow(0)
    val scrollIndex: StateFlow<Int> = _scrollIndex
    
    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset
    
    /**
     * Save the current scroll position from LazyGridState
     */
    fun saveScrollPosition(state: LazyGridState) {
        _scrollIndex.value = state.firstVisibleItemIndex
        _scrollOffset.value = state.firstVisibleItemScrollOffset
    }
    
    /**
     * Get saved scroll index
     */
    fun getSavedScrollIndex(): Int = _scrollIndex.value
    
    /**
     * Get saved scroll offset
     */
    fun getSavedScrollOffset(): Int = _scrollOffset.value
    
    /**
     * Reset scroll position (call when clearing template selection)
     */
    fun reset() {
        _scrollIndex.value = 0
        _scrollOffset.value = 0
    }
}
