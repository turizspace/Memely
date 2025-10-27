package com.memely.ui.tutorial

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages tutorial state and progression
 */
object TutorialManager {
    private const val PREFS_NAME = "memely_tutorial_prefs"
    private const val KEY_COMPLETED = "tutorial_completed"
    private const val KEY_SKIPPED = "tutorial_skipped"
    private const val KEY_CURRENT_STEP = "current_step_index"
    
    private lateinit var prefs: SharedPreferences
    
    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()
    
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _currentScreen = MutableStateFlow<TutorialScreen?>(null)
    val currentScreen: StateFlow<TutorialScreen?> = _currentScreen.asStateFlow()
    
    // Callback for navigation actions
    var onNavigationRequired: ((String) -> Unit)? = null
    
    private val tutorialSteps = listOf(
        // Home Screen Steps
        TutorialStep(
            id = "home_search",
            screen = TutorialScreen.HOME_FEED,
            targetTag = "search_bar",
            title = "Search Templates",
            description = "Use the search bar to find specific templates quickly.",
            tooltipPosition = TooltipPosition.BOTTOM,
            highlightType = HighlightType.NONE
        ),
        TutorialStep(
            id = "home_select_template",
            screen = TutorialScreen.HOME_FEED,
            targetTag = "template_grid",
            title = "Select a Template",
            description = "Click Next to open the meme editor with a template.",
            tooltipPosition = TooltipPosition.BOTTOM,
            highlightType = HighlightType.NONE
        ),
        
        // Meme Editor Steps
        TutorialStep(
            id = "editor_canvas",
            screen = TutorialScreen.MEME_EDITOR,
            targetTag = "meme_canvas",
            title = "Meme Canvas",
            description = "This is your creative space. Tap and drag to position text and images.",
            tooltipPosition = TooltipPosition.BOTTOM,
            highlightType = HighlightType.NONE
        ),
        TutorialStep(
            id = "editor_add_text",
            screen = TutorialScreen.MEME_EDITOR,
            targetTag = "btn_add_text",
            title = "Add Text",
            description = "Tap here to add text to your meme. You can add multiple text layers.",
            tooltipPosition = TooltipPosition.TOP,
            highlightType = HighlightType.NONE
        ),
        TutorialStep(
            id = "editor_add_image",
            screen = TutorialScreen.MEME_EDITOR,
            targetTag = "btn_add_image",
            title = "Add Image Overlay",
            description = "Add additional images or stickers to your meme.",
            tooltipPosition = TooltipPosition.TOP,
            highlightType = HighlightType.NONE
        ),
        TutorialStep(
            id = "editor_save",
            screen = TutorialScreen.MEME_EDITOR,
            targetTag = "btn_save",
            title = "Save Your Meme",
            description = "Save your meme to your device.",
            tooltipPosition = TooltipPosition.TOP,
            highlightType = HighlightType.NONE
        ),
        TutorialStep(
            id = "editor_post",
            screen = TutorialScreen.MEME_EDITOR,
            targetTag = "btn_post_nostr",
            title = "Share on Nostr",
            description = "Post your meme directly to Nostr to share with the world! Click Next to explore the feed.",
            tooltipPosition = TooltipPosition.TOP,
            highlightType = HighlightType.NONE
        ),
        
        // Explore Tab
        TutorialStep(
            id = "explore_feed",
            screen = TutorialScreen.EXPLORE,
            targetTag = "meme_feed",
            title = "Meme Timeline",
            description = "Browse memes shared on Nostr. Scroll through the timeline to discover content.",
            tooltipPosition = TooltipPosition.BOTTOM,
            highlightType = HighlightType.NONE
        ),
        TutorialStep(
            id = "explore_interactions",
            screen = TutorialScreen.EXPLORE,
            targetTag = "interaction_controls",
            title = "Interact with Memes",
            description = "Reply to memes, react with likes, or repost to share with your followers. Click Next to see upload features.",
            tooltipPosition = TooltipPosition.TOP,
            highlightType = HighlightType.NONE
        ),
        
        // Upload Tab
        TutorialStep(
            id = "upload_button",
            screen = TutorialScreen.UPLOAD,
            targetTag = "select_image_button",
            title = "Select from Device",
            description = "Choose an image from your device to use as a template or create a meme. Click Next to see your profile.",
            tooltipPosition = TooltipPosition.BOTTOM,
            highlightType = HighlightType.NONE
        ),
        
        // Profile Tab
        TutorialStep(
            id = "profile_info",
            screen = TutorialScreen.PROFILE,
            targetTag = "profile_content",
            title = "Profile Information",
            description = "Your Nostr profile shows your name, bio, and connection status.",
            tooltipPosition = TooltipPosition.BOTTOM,
            highlightType = HighlightType.NONE
        ),
        TutorialStep(
            id = "tutorial_complete",
            screen = TutorialScreen.PROFILE,
            targetTag = "profile_content",
            title = "Tutorial Complete! 🎉",
            description = "You're ready to create amazing memes! Tap 'Got it!' to start creating.",
            tooltipPosition = TooltipPosition.CENTER,
            highlightType = HighlightType.NONE
        )
    )
    
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _currentStepIndex.value = prefs.getInt(KEY_CURRENT_STEP, 0)
    }
    
    fun startTutorial() {
        _isActive.value = true
        _currentStepIndex.value = 0
        _currentScreen.value = tutorialSteps.firstOrNull()?.screen
        saveProgress()
    }
    
    fun nextStep() {
        if (_currentStepIndex.value < tutorialSteps.size - 1) {
            val currentStep = tutorialSteps[_currentStepIndex.value]
            val nextStep = tutorialSteps[_currentStepIndex.value + 1]
            
            // Trigger navigation if needed before advancing
            when (currentStep.id) {
                "home_select_template" -> {
                    // Navigate to meme editor
                    onNavigationRequired?.invoke("navigate_to_editor")
                }
                "editor_post" -> {
                    // Navigate to explore tab
                    onNavigationRequired?.invoke("navigate_to_explore")
                }
                "explore_interactions" -> {
                    // Navigate to upload tab
                    onNavigationRequired?.invoke("navigate_to_upload")
                }
                "upload_button" -> {
                    // Navigate to profile tab
                    onNavigationRequired?.invoke("navigate_to_profile")
                }
            }
            
            _currentStepIndex.value++
            _currentScreen.value = nextStep.screen
            saveProgress()
        } else {
            completeTutorial()
        }
    }
    
    fun previousStep() {
        if (_currentStepIndex.value > 0) {
            val currentStep = tutorialSteps[_currentStepIndex.value]
            
            // Trigger reverse navigation if needed before going back
            when (currentStep.id) {
                "editor_canvas" -> {
                    // Going back from editor to home
                    onNavigationRequired?.invoke("navigate_to_home")
                }
                "explore_feed" -> {
                    // Going back from explore to editor
                    onNavigationRequired?.invoke("navigate_to_editor")
                }
                "upload_button" -> {
                    // Going back from upload to explore
                    onNavigationRequired?.invoke("navigate_to_explore")
                }
                "profile_info" -> {
                    // Going back from profile to upload
                    onNavigationRequired?.invoke("navigate_to_upload")
                }
            }
            
            _currentStepIndex.value--
            _currentScreen.value = tutorialSteps[_currentStepIndex.value].screen
            saveProgress()
        }
    }
    
    fun skipTutorial() {
        _isActive.value = false
        prefs.edit().putBoolean(KEY_SKIPPED, true).apply()
    }
    
    fun completeTutorial() {
        _isActive.value = false
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }
    
    fun resetTutorial() {
        _isActive.value = false
        _currentStepIndex.value = 0
        prefs.edit()
            .remove(KEY_COMPLETED)
            .remove(KEY_SKIPPED)
            .remove(KEY_CURRENT_STEP)
            .apply()
    }
    
    fun isTutorialCompleted(): Boolean {
        return prefs.getBoolean(KEY_COMPLETED, false)
    }
    
    fun isTutorialSkipped(): Boolean {
        return prefs.getBoolean(KEY_SKIPPED, false)
    }
    
    fun shouldShowTutorial(): Boolean {
        return !isTutorialCompleted() && !isTutorialSkipped()
    }
    
    fun getCurrentStep(): TutorialStep? {
        return tutorialSteps.getOrNull(_currentStepIndex.value)
    }
    
    fun getStepsForScreen(screen: TutorialScreen): List<TutorialStep> {
        return tutorialSteps.filter { it.screen == screen }
    }
    
    fun getTotalSteps(): Int = tutorialSteps.size
    
    private fun saveProgress() {
        prefs.edit().putInt(KEY_CURRENT_STEP, _currentStepIndex.value).apply()
    }
}
