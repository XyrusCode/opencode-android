package com.xyrus.opencode.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Refined Apple-inspired dark palette (mirrors Video-Plucker's red-accent glass UI)
val Bg = Color(0xFF0C0D0F)
val Panel = Color(0xFF1C1C1E)
val Panel2 = Color(0xFF2C2C2E)
val BorderCol = Color(0xFF3A3A3C)
val TextCol = Color(0xFFF5F5F7)
val TextDim = Color(0x99F5F5F7) // 60% opacity text
val Accent = Color(0xFFFF2D55) // Apple system red (branded app)
val AccentDim = Color(0xFFBF1A3D)
val Ok = Color(0xFF30D158) // Apple system green
val Warn = Color(0xFFFF9F0A) // Apple system orange

val AccentGlow = Color(0xFFFF375F)
val AccentSoft = Color(0x1AFF2D55) // 10% opacity
val AccentSoftStrong = Color(0x33FF2D55) // 20% opacity
val GlassSurface = Color(0xB31C1C1E) // 70% opacity panel
val GlassBorder = Color(0x1AFFFFFF) // 10% white
val OkGlow = Color(0xFF34C759)
val WarnGlow = Color(0xFFFFCC02)

val AccentGradientHorizontal = Brush.horizontalGradient(
    colors = listOf(Accent, AccentGlow),
)
val AccentGradientVertical = Brush.verticalGradient(
    colors = listOf(AccentGlow, Accent),
)
val AccentGradientDiagonal = Brush.linearGradient(
    colors = listOf(Accent, AccentGlow, Accent),
)
val GlassGradient = Brush.verticalGradient(
    colors = listOf(GlassSurface, GlassSurface.copy(alpha = 0.85f)),
)

/** Critically damped spring — the default for all UI motion. */
val MotionSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
/** Slight bounce, reserved for flicks/drags. */
val MomentumSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

private val OpencodeColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentDim,
    background = Bg,
    onBackground = TextCol,
    surface = Panel,
    onSurface = TextCol,
    surfaceVariant = Panel2,
    onSurfaceVariant = TextDim,
    outline = BorderCol,
    error = Warn,
)

@Composable
fun OpencodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OpencodeColors,
        content = content,
    )
}

private fun DrawScope.drawGlowCircle(color: Color, radiusPx: Float) {
    drawCircle(
        color = color,
        radius = radiusPx,
        center = Offset(size.width / 2f, size.height / 2f),
        blendMode = BlendMode.Screen,
    )
    drawCircle(
        color = color.copy(alpha = color.alpha * 0.5f),
        radius = radiusPx * 1.5f,
        center = Offset(size.width / 2f, size.height / 2f),
        blendMode = BlendMode.Screen,
    )
}

@Stable
fun Modifier.glow(
    color: Color = Accent,
    radius: Dp = 12.dp,
): Modifier = this.drawBehind {
    val radiusPx = radius.toPx()
    drawGlowCircle(color, radiusPx)
}

@Composable
fun Modifier.pulsingGlow(
    color: Color = Accent,
    radius: Dp = 16.dp,
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    return this.glow(color.copy(alpha = alpha), radius)
}

@Stable
fun Modifier.accentGlowLine(
    color: Color = Accent,
    height: Dp = 2.dp,
    radius: Dp = 6.dp,
): Modifier = this.drawBehind {
    val hPx = height.toPx()
    val rPx = radius.toPx()
    drawLine(
        color = color,
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = hPx,
        blendMode = BlendMode.Screen,
    )
    drawLine(
        color = color.copy(alpha = 0.5f),
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = hPx * 3f,
        blendMode = BlendMode.Screen,
    )
}
