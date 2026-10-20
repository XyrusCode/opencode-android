package com.xyrus.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.BorderCol
import com.xyrus.opencode.ui.theme.Panel
import com.xyrus.opencode.ui.theme.Panel2
import com.xyrus.opencode.ui.theme.PlexMono
import com.xyrus.opencode.ui.theme.TextCol
import com.xyrus.opencode.ui.theme.TextDim

/**
 * Markdown for assistant chat.
 *
 * Parsing lives in [Markdown.kt]; this file only turns the resulting blocks into
 * composables. Code fences are the thing that matters most in a coding harness,
 * so they get a language header and their own scroll rather than being squeezed
 * into the text column.
 */
@Composable
fun ChatMarkdown(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> Heading(block)
                is Block.Paragraph -> Text(
                    text = spans(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextCol,
                )

                is Block.Bullet -> BulletRow(block)
                is Block.Code -> CodeBlock(block)
                is Block.Quote -> Quote(block)
                Block.Rule -> HorizontalDivider(color = BorderCol, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun Heading(block: Block.Heading) {
    val size = when (block.level) {
        1 -> 19.sp
        2 -> 17.sp
        3 -> 15.sp
        else -> 14.sp
    }
    Text(
        text = spans(block.text),
        color = TextCol,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        lineHeight = size * 1.35f,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun BulletRow(block: Block.Bullet) {
    Row(Modifier.padding(start = (block.indent * 4).dp)) {
        if (block.checked != null) {
            Icon(
                imageVector = if (block.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = if (block.checked) "Done" else "Not done",
                tint = if (block.checked) Accent else TextDim,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Text(
                text = "${block.marker}",
                color = TextDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(if (block.marker.length == 1) 14.dp else 20.dp),
            )
        }
        Text(
            text = spans(block.text),
            style = MaterialTheme.typography.bodyMedium,
            color = TextCol,
        )
    }
}

@Composable
private fun Quote(block: Block.Quote) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel2.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(width = 2.dp, height = 16.dp)
                .background(Accent.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = spans(block.text),
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
        )
    }
}

@Composable
private fun CodeBlock(block: Block.Code) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, BorderCol, shape),
    ) {
        if (block.lang.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Panel2)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    block.lang,
                    fontFamily = PlexMono,
                    fontSize = 11.sp,
                    color = TextDim,
                )
            }
        }
        // Scroll horizontally inside the block. An earlier version scrolled the
        // whole row, which took the page with it and swallowed vertical scrolls.
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = block.code,
                fontFamily = PlexMono,
                fontSize = 13.sp,
                color = TextCol,
                softWrap = false,
            )
        }
    }
}

private fun spans(src: String): AnnotatedString = buildAnnotatedString {
    fun style(type: InlineSpan): SpanStyle = when (type) {
        is InlineSpan.Plain -> SpanStyle()
        is InlineSpan.Bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
        is InlineSpan.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        is InlineSpan.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = TextDim)
        is InlineSpan.Code -> SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = Panel2,
            color = Accent,
            fontSize = 13.sp,
        )

        // The label is what reads; the URL would blow out the line width.
        is InlineSpan.Link -> SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)
    }

    for (span in inlineSpans(src)) {
        withStyle(style(span)) { append(span.text) }
    }
}

/** Plain-text rendering of the same grammar, for previews and tests. */
internal fun markdownToPlain(src: String): String =
    parseMarkdown(src).joinToString("\n") { block ->
        when (block) {
            is Block.Heading -> block.text
            is Block.Paragraph -> block.text
            is Block.Bullet -> " ".repeat(block.indent / 2) + "${block.marker} ${block.text}"
            is Block.Code -> block.code
            is Block.Quote -> block.text
            Block.Rule -> "---"
        }
    }