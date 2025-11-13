package com.memely.ui.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.max

/**
 * A floating coach mark that highlights a UI element with a tooltip
 */
@Composable
fun CoachMark(
    step: TutorialStep,
    targetBounds: TargetBounds?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    currentStepIndex: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    // Animation for pulsing highlight
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1000f)
    ) {
        // Highlight border overlay (no dark background, allows clicks through)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Draw only the highlight borders, no dark overlay
            targetBounds?.let { bounds ->
                when (step.highlightType) {
                    HighlightType.CIRCLE -> {
                        val radius = max(bounds.rect.width, bounds.rect.height) / 2f + 16.dp.toPx()
                        // Draw pulsing border only
                        drawCircle(
                            color = Color(0xFF8B5CF6).copy(alpha = 0.9f), // Purple border
                            radius = radius * pulseScale,
                            center = bounds.center,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                    HighlightType.RECTANGLE -> {
                        val padding = 8.dp.toPx()
                        drawRect(
                            color = Color(0xFF8B5CF6).copy(alpha = 0.9f), // Purple border
                            topLeft = Offset(
                                bounds.rect.left - padding,
                                bounds.rect.top - padding
                            ),
                            size = Size(
                                bounds.rect.width + padding * 2,
                                bounds.rect.height + padding * 2
                            ),
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                    HighlightType.ROUNDED_RECTANGLE -> {
                        val padding = 8.dp.toPx()
                        val cornerRadius = 12.dp.toPx()
                        drawRoundRect(
                            color = Color(0xFF8B5CF6).copy(alpha = 0.9f), // Purple border
                            topLeft = Offset(
                                bounds.rect.left - padding,
                                bounds.rect.top - padding
                            ),
                            size = Size(
                                bounds.rect.width + padding * 2,
                                bounds.rect.height + padding * 2
                            ),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                    HighlightType.NONE -> {
                        // No highlight cutout
                    }
                }
            }
        }
        
        // Draw pointer arrow from tooltip to target
        targetBounds?.let { bounds ->
            PointerArrow(
                targetCenter = bounds.center,
                tooltipPosition = step.tooltipPosition,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Tooltip card
        TooltipCard(
            step = step,
            onNext = onNext,
            onPrevious = onPrevious,
            onSkip = onSkip,
            currentStepIndex = currentStepIndex,
            totalSteps = totalSteps,
            modifier = Modifier.align(
                when (step.tooltipPosition) {
                    TooltipPosition.TOP -> Alignment.TopCenter
                    TooltipPosition.BOTTOM -> Alignment.BottomCenter
                    TooltipPosition.LEFT -> Alignment.CenterStart
                    TooltipPosition.RIGHT -> Alignment.CenterEnd
                    TooltipPosition.CENTER -> Alignment.Center
                }
            )
        )
    }
}

@Composable
private fun TooltipCard(
    step: TutorialStep,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    currentStepIndex: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .widthIn(max = 320.dp),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with step counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${currentStepIndex + 1} of $totalSteps",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                IconButton(
                    onClick = onSkip,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Skip tutorial",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Title
            Text(
                text = step.title,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                fontSize = 15.sp
            )
            
            // Description
            Text(
                text = step.description,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            
            // Action hint if required
            if (step.actionRequired && step.actionDescription != null) {
                Text(
                    text = "👉 ${step.actionDescription}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colors.secondary.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            
            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous button
                if (currentStepIndex > 0) {
                    TextButton(
                        onClick = onPrevious,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Previous", fontSize = 13.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                // Next/Got it button
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (currentStepIndex < totalSteps - 1) "Next" else "Got it!",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    if (currentStepIndex < totalSteps - 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Arrow pointer from tooltip to target
 */
@Composable
private fun PointerArrow(
    targetCenter: Offset,
    tooltipPosition: TooltipPosition,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Animation for pulsing hand emoji
    val infiniteTransition = rememberInfiniteTransition(label = "handPulse")
    val handScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "handScale"
    )

    Box(modifier = modifier) {
        // Hand emoji pointer positioned relative to the target's center
        val emojiSize = 32.sp
        val emojiSizeDp = with(density) { emojiSize.toDp() }
        val halfEmojiSize = emojiSizeDp / 2

        // Directional offset to make the hand point *at* the target
        val directionalOffset = with(density) {
            when (tooltipPosition) {
                TooltipPosition.BOTTOM -> Offset(0f, -32.dp.toPx()) // Move up
                TooltipPosition.TOP -> Offset(0f, 32.dp.toPx())    // Move down
                TooltipPosition.RIGHT -> Offset(-32.dp.toPx(), 0f) // Move left
                TooltipPosition.LEFT -> Offset(32.dp.toPx(), 0f)   // Move right
                TooltipPosition.CENTER -> Offset(0f, -32.dp.toPx())
            }
        }

        val finalCenterUnclamped = targetCenter + directionalOffset

        // Get screen bounds (in px) and clamp the emoji so it stays visible on devices
        val configuration = LocalConfiguration.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val halfEmojiPx = with(density) { halfEmojiSize.toPx() }

        val finalCenter = Offset(
            x = finalCenterUnclamped.x.coerceIn(halfEmojiPx, screenWidthPx - halfEmojiPx),
            y = finalCenterUnclamped.y.coerceIn(halfEmojiPx, screenHeightPx - halfEmojiPx)
        )

     
        Text(
            text = when (tooltipPosition) {
                TooltipPosition.BOTTOM -> "👆"  // Pointing up
                TooltipPosition.TOP -> "👇"     // Pointing down
                TooltipPosition.RIGHT -> "👈"  // Pointing left
                TooltipPosition.LEFT -> "👉"   // Pointing right
                TooltipPosition.CENTER -> "👆"  // Default pointing up
            },
            modifier = Modifier
                .offset(
                    x = with(density) { finalCenter.x.toDp() - halfEmojiSize },
                    y = with(density) { finalCenter.y.toDp() - halfEmojiSize }
                )
                .graphicsLayer(
                    scaleX = handScale,
                    scaleY = handScale,
                    alpha = 0.95f
                )
                .zIndex(1001f),
            fontSize = emojiSize
        )
    }
}

private fun DrawScope.drawArrow(
    from: Offset,
    to: Offset,
    color: Color
) {
    val path = Path().apply {
        moveTo(from.x, from.y)
        lineTo(to.x, to.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.dp.toPx())
    )
}
