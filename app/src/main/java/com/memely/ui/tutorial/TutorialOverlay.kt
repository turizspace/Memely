package com.memely.ui.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex

/**
 * Main tutorial overlay that manages and displays coach marks
 */
@Composable
fun TutorialOverlay(
    currentScreen: TutorialScreen,
    modifier: Modifier = Modifier
) {
    val isActive by TutorialManager.isActive.collectAsState()
    val currentStepIndex by TutorialManager.currentStepIndex.collectAsState()
    val tutorialScreen by TutorialManager.currentScreen.collectAsState()
    
    // Track target element positions
    val targetPositions = remember { mutableStateMapOf<String, TargetBounds>() }
    
    // Get current step
    val currentStep = TutorialManager.getCurrentStep()
    
    // Only show tutorial if active and on the correct screen
    if (isActive && tutorialScreen == currentScreen && currentStep != null) {
        val targetBounds = targetPositions[currentStep.targetTag]
      
        CoachMark(
            step = currentStep,
            targetBounds = targetBounds,
            onNext = {
                if (currentStep.actionRequired && targetBounds == null) {
                    // Wait for user to perform the action
                    return@CoachMark
                }
                TutorialManager.nextStep()
            },
            onPrevious = {
                TutorialManager.previousStep()
            },
            onSkip = {
                TutorialManager.skipTutorial()
            },
            currentStepIndex = currentStepIndex,
            totalSteps = TutorialManager.getTotalSteps(),
            modifier = modifier.zIndex(1000f)
        )
    }
    
    // Provide a way to update target positions
    DisposableEffect(currentScreen) {
        // Clear previous target positions when screen changes
        targetPositions.clear()
        
        TutorialTargetRegistry.setPositionUpdater { tag, bounds ->
            targetPositions[tag] = bounds
            // Debug logging
        }

        // Force a reposition/pass of any already-registered targets. This handles the case
        // where targets (especially those in Scaffold.bottomBar) were positioned before
        // the overlay's position updater was registered.
        TutorialTargetRegistry.repositionAllTargets()
        
        onDispose {
            TutorialTargetRegistry.clearPositionUpdater()
        }
    }
}

/**
 * Registry to track UI element positions for targeting
 */
object TutorialTargetRegistry {
    private var positionUpdater: ((String, TargetBounds) -> Unit)? = null
    private val targets = mutableMapOf<String, LayoutCoordinates>()

    fun setPositionUpdater(updater: (String, TargetBounds) -> Unit) {
        positionUpdater = updater
    }
    
    fun clearPositionUpdater() {
        positionUpdater = null
    }

    fun register(tag: String, coordinates: LayoutCoordinates) {
        targets[tag] = coordinates
        updatePosition(tag, coordinates)
    }

    fun unregister(tag: String) {
        targets.remove(tag)
    }
    
    fun updatePosition(tag: String, coordinates: LayoutCoordinates) {
        val bounds = coordinates.boundsInRoot()
        if (bounds.width > 0 && bounds.height > 0) {
            positionUpdater?.invoke(tag, TargetBounds.fromRect(bounds))
        }
    }

    fun repositionAllTargets() {
        targets.forEach { (tag, coordinates) ->
            updatePosition(tag, coordinates)
        }
    }
}

/**
 * Modifier to mark UI elements as tutorial targets
 */
fun Modifier.tutorialTarget(tag: String): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    var layoutCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }

    DisposableEffect(tag) {
        onDispose {
            TutorialTargetRegistry.unregister(tag)
        }
    }

    this.onGloballyPositioned { coordinates ->
        layoutCoordinates = coordinates
        TutorialTargetRegistry.register(tag, coordinates)
    }
}

/**
 * Helper composable to start tutorial from any screen
 */
@Composable
fun rememberTutorialState(): TutorialState {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        TutorialManager.initialize(context)
    }
    
    val isCompleted = remember { TutorialManager.isTutorialCompleted() }
    val currentStepIndex by TutorialManager.currentStepIndex.collectAsState()
    
    return TutorialState(
        isCompleted = isCompleted,
        currentStepIndex = currentStepIndex,
        completedSteps = emptySet(),
        skipTutorial = TutorialManager.isTutorialSkipped()
    )
}
