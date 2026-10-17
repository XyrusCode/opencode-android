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
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xyrus.opencode.R

// opencode brand palette (see the opencode DESIGN.md). Warm near-black canvas,
// cream ink, peach primary + purple secondary. Monospace-first typography.

val Bg = Color(0xFF0F0D0D) // warm canvas (app background)
val Panel = Color(0xFF201D1D) // cards / composer surface — the brand ink
val Panel2 = Color(0xFF2A2525) // elevated surfaces, user bubble
val BorderCol = Color(0xFF332E2E) // hairline border
val TextCol = Color(0xFFF1ECEC) // cream ink — primary text
val TextDim = Color(0xFF9A9898) // muted / ash — secondary text

val Accent = Color(0xFFFAB283) // peach — primary accent (brand primary)
val AccentDim = Color(0xFFC98A5E)
val Secondary = Color(0xFF9D7CD8) // purple — secondary accent
val Ok = Color(0xFF7FD88F) // success green
val Warn = Color(0xFFF5A742) // warning amber
val Err = Color(0xFFE06C75) // error red
val Info = Color(0xFF56B6C2)

// Derived / accent aliases used by the component library (kept for compatibility).
val AccentGlow = Color(0xFFFFC9A3) // light peach for gradients
val AccentSoft = Color(0x1AFAB283) // 10% peach
val AccentSoftStrong = Color(0x33FAB283) // 20% peach
val GlassSurface = Color(0xFF201D1D)
val GlassBorder = Color(0x1AF1ECEC) // 10% cream hairline
val OkGlow = Ok
val WarnGlow = Warn

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
    colors = listOf(GlassSurface, Panel),
)

/** Brand monospace (IBM Plex Mono — the free stand-in for the commercial Berkeley Mono). */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Critically damped spring — the default for all UI motion. */
val MotionSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
/** Slight bounce, reserved for flicks/drags. */
val MomentumSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

private fun plex(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = PlexMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

private val OpencodeTypography = Typography(
    displayLarge = plex(48, 56, FontWeight.SemiBold),
    displayMedium = plex(40, 48, FontWeight.SemiBold),
    displaySmall = plex(34, 42, FontWeight.SemiBold),
    headlineLarge = plex(30, 38, FontWeight.SemiBold),
    headlineMedium = plex(26, 34, FontWeight.SemiBold),
    headlineSmall = plex(22, 30, FontWeight.SemiBold),
    titleLarge = plex(20, 28, FontWeight.Medium),
    titleMedium = plex(16, 24, FontWeight.Medium),
    titleSmall = plex(14, 20, FontWeight.Medium),
    bodyLarge = plex(15, 24),
    bodyMedium = plex(14, 22),
    bodySmall = plex(13, 20),
    labelLarge = plex(14, 20, FontWeight.Medium),
    labelMedium = plex(12, 16, FontWeight.Medium),
    labelSmall = plex(11, 16, FontWeight.Medium),
)

private val OpencodeColors = darkColorScheme(
    primary = Accent,
    onPrimary = Bg,
    secondary = Secondary,
    onSecondary = Bg,
    background = Bg,
    onBackground = TextCol,
    surface = Panel,
    onSurface = TextCol,
    surfaceVariant = Panel2,
    onSurfaceVariant = TextDim,
    outline = BorderCol,
    error = Err,
)

@Composable
fun OpencodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OpencodeColors,
        typography = OpencodeTypography,
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
    radius: androidx.compose.ui.unit.Dp = 12.dp,
): Modifier = this.drawBehind {
    val radiusPx = radius.toPx()
    drawGlowCircle(color, radiusPx)
}

@Composable
fun Modifier.pulsingGlow(
    color: Color = Accent,
    radius: androidx.compose.ui.unit.Dp = 16.dp,
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
