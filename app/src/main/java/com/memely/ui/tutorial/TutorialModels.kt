package com.memely.ui.tutorial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/**
 * Represents a single step in the interactive tutorial
 */
data class TutorialStep(
    val id: String,
    val screen: TutorialScreen,
    val targetTag: String,
    val title: String,
    val description: String,
    val tooltipPosition: TooltipPosition = TooltipPosition.BOTTOM,
    val highlightType: HighlightType = HighlightType.CIRCLE,
    val actionRequired: Boolean = false,
    val actionDescription: String? = null
)

/**
 * Available screens in the app for tutorials
 */
enum class TutorialScreen {
    HOME_FEED,
    EXPLORE,
    UPLOAD,
    PROFILE,
    MEME_EDITOR
}

/**
 * Position of the tooltip relative to the highlighted element
 */
enum class TooltipPosition {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    CENTER
}

/**
 * Type of highlight shape around the UI element
 */
enum class HighlightType {
    CIRCLE,
    RECTANGLE,
    ROUNDED_RECTANGLE,
    NONE
}

/**
 * Represents the bounds and position of a UI element to highlight
 */
data class TargetBounds(
    val rect: Rect,
    val center: Offset
) {
    companion object {
        fun fromRect(rect: Rect) = TargetBounds(
            rect = rect,
            center = Offset(
                x = rect.left + (rect.width / 2f),
                y = rect.top + (rect.height / 2f)
            )
        )
    }
}

/**
 * Tutorial state for persistence
 */
data class TutorialState(
    val isCompleted: Boolean = false,
    val currentStepIndex: Int = 0,
    val completedSteps: Set<String> = emptySet(),
    val skipTutorial: Boolean = false
)
