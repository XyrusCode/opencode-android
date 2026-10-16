package com.xyrus.opencode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.AccentSoft
import com.xyrus.opencode.ui.theme.GlassBorder
import com.xyrus.opencode.ui.theme.GlassSurface
import com.xyrus.opencode.ui.theme.Panel

private val CardRoundedShape = RoundedCornerShape(14.dp)

/** Semi-transparent glass card with a subtle border. Apple-inspired frosted surface. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorder,
    backgroundColor: Color = GlassSurface,
    cornerRadius: Dp = 14.dp,
    glowOnAccent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (glowOnAccent) Modifier.background(AccentSoft)
                else Modifier.background(backgroundColor)
            )
            .border(width = 1.dp, color = borderColor, shape = shape),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

/** An accent-tinted surface — for cards that need a light red tint (e.g. active download). */
@Composable
fun AccentTintCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(AccentSoft)
            .border(width = 1.dp, color = Accent.copy(alpha = 0.15f), shape = shape),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}
