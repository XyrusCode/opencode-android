package com.xyrus.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xyrus.opencode.ui.theme.BorderCol
import com.xyrus.opencode.ui.theme.Panel
import com.xyrus.opencode.ui.theme.Panel2
import com.xyrus.opencode.ui.theme.PlexMono
import com.xyrus.opencode.ui.theme.TextCol

/**
 * A small markdown renderer tuned for assistant chat: fenced code blocks (the
 * single most important thing for a coding harness), bold, inline code, bullets
 * and headings. Deliberately not a full markdown implementation — it covers what
 * models actually emit without pulling in a dependency.
 */
@Composable
fun ChatMarkdown(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(block.code)
                is MdBlock.Text -> Text(
                    text = inlineMd(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextCol,
                )
            }
        }
    }
}

private sealed interface MdBlock {
    data class Text(val text: String) : MdBlock
    data class Code(val code: String) : MdBlock
}

private fun parseBlocks(md: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val buf = StringBuilder()
    val lines = md.lines()
    var i = 0
    fun flush() {
        val t = buf.toString().trim()
        if (t.isNotEmpty()) blocks += MdBlock.Text(t)
        buf.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            flush()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            i++ // skip the closing fence
            blocks += MdBlock.Code(code.toString().trimEnd())
        } else {
            buf.appendLine(line)
            i++
        }
    }
    flush()
    return blocks
}

@Composable
private fun CodeBlock(code: String) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, BorderCol, shape),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Text(
                text = code,
                fontFamily = PlexMono,
                fontSize = 13.sp,
                color = TextCol,
            )
        }
    }
}

private fun inlineMd(src: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    fun lineStart() = i == 0 || src[i - 1] == '\n'
    while (i < src.length) {
        val c = src[i]
        val next = if (i + 1 < src.length) src[i + 1] else ' '
        when {
            c == '*' && next == '*' -> {
                val end = src.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(src.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(c); i++
                }
            }

            c == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(
                        SpanStyle(
                            fontFamily = PlexMono,
                            background = Panel2,
                            fontSize = 13.sp,
                        ),
                    ) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(c); i++
                }
            }

            c == '#' && lineStart() && next == ' ' -> {
                i += 2 // drop the heading marker; mono body keeps it readable
            }

            (c == '-' || c == '*') && next == ' ' && lineStart() -> {
                append("• ")
                i += 2
            }

            else -> {
                append(c); i++
            }
        }
    }
}