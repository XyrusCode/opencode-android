package com.xyrus.opencode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.AccentGlow

/**
 * Gradient-filled progress bar with an animated shimmer.
 * Replaces the default LinearProgressIndicator for download progress.
 */
@Composable
fun GradientProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    colors: List<Color> = listOf(Accent, AccentGlow),
) {
    val shape = RoundedCornerShape(height / 2)

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    // Offset the gradient brush each frame to travel the shimmer across the bar.
    val animatedGradient = Brush.horizontalGradient(
        colors = listOf(
            colors.first().copy(alpha = 0.9f),
            colors.last().copy(alpha = 1f),
            colors.first().copy(alpha = 0.9f),
        ),
        startX = shimmerOffset,
        endX = shimmerOffset + 400f,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Color(0xFF2A2F3A)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .height(height)
                .clip(shape)
                .background(animatedGradient),
        )
    }
}
