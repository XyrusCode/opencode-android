package com.xyrus.opencode.ui

/**
 * A line-oriented markdown parser scoped to what models actually emit in chat:
 * headings, paragraphs, bullet and numbered lists, task lists, fenced code,
 * blockquotes and horizontal rules. Tables are the one thing it skips, because
 * a markdown table flattened into one run of pipes is worse than no table.
 *
 * This is pure and separate from the Compose layer so the grammar can be tested
 * directly.
 */
internal sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block

    data class Paragraph(val text: String) : Block

    /** [indent] is the leading-space width, used to nest. */
    data class Bullet(
        val indent: Int,
        val marker: String,
        val text: String,
        val checked: Boolean?,
    ) : Block

    data class Code(val lang: String, val code: String) : Block

    data class Quote(val text: String) : Block

    data object Rule : Block
}

private val BULLET = Regex("^(\\s*)([-*+])\\s+(.*)$")
private val ORDERED = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
private val TASK = Regex("^\\[[ xX]\\]\\s+(.*)$")
private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val FENCE = Regex("^\\s{0,3}(```|~~~)\\s*(\\S+)?")

internal fun parseMarkdown(src: String): List<Block> {
    val lines = src.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = mutableListOf<Block>()
    val para = mutableListOf<String>()

    fun flushPara() {
        if (para.isEmpty()) return
        val joined = para.joinToString("\n").trim()
        if (joined.isNotEmpty()) out += Block.Paragraph(joined)
        para.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val fence = FENCE.find(line)

        if (fence != null) {
            flushPara()
            val marker = fence.groupValues[1]
            val lang = fence.groupValues[2]
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith(marker)) {
                code.appendLine(lines[i])
                i++
            }
            // An unterminated fence still renders as code; dropping it would hide
            // whatever the model was in the middle of writing.
            if (i < lines.size) i++
            out += Block.Code(lang, code.toString().trimEnd())
            continue
        }

        if (line.isBlank()) {
            flushPara()
            i++
            continue
        }

        // A rule is checked before bullets: `---` and `***` both match one.
        if (RULE.matches(line)) {
            flushPara()
            out += Block.Rule
            i++
            continue
        }

        val heading = HEADING.find(line)
        if (heading != null) {
            flushPara()
            out += Block.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        val bullet = BULLET.find(line)
        if (bullet != null) {
            flushPara()
            val body = bullet.groupValues[3]
            val task = TASK.find(body)
            out += Block.Bullet(
                indent = bullet.groupValues[1].length,
                marker = "•",
                text = if (task != null) task.groupValues[1] else body,
                checked = task?.let { it.groupValues[0].startsWith("[x", ignoreCase = true) },
            )
            i++
            continue
        }

        val ordered = ORDERED.find(line)
        if (ordered != null) {
            flushPara()
            out += Block.Bullet(
                indent = ordered.groupValues[1].length,
                marker = "${ordered.groupValues[2]}.",
                text = ordered.groupValues[3],
                checked = null,
            )
            i++
            continue
        }

        if (line.trimStart().startsWith(">")) {
            flushPara()
            val quote = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                quote += lines[i].trimStart().removePrefix(">").trim()
                i++
            }
            out += Block.Quote(quote.joinToString("\n"))
            continue
        }

        // A plain line continues the current paragraph.
        para += line
        i++
    }
    flushPara()
    return out
}

/**
 * Inline markers, applied in one left-to-right pass.
 *
 * Code spans are matched first and their contents are never rescanned, so a
 * literal `**` inside backticks stays literal.
 */
internal fun inlineSpans(src: String): List<InlineSpan> {
    val out = mutableListOf<InlineSpan>()
    val buf = StringBuilder()
    var i = 0

    fun flush() {
        if (buf.isNotEmpty()) {
            out += InlineSpan.Plain(buf.toString())
            buf.clear()
        }
    }

    fun take(start: Int, end: Int, build: (String) -> InlineSpan) {
        flush()
        out += build(src.substring(start, end))
    }

    while (i < src.length) {
        val c = src[i]
        when {
            c == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i) {
                    take(i + 1, end) { InlineSpan.Code(it) }
                    i = end + 1
                    continue
                }
                buf.append(c); i++
            }

            src.startsWith("**", i) -> {
                val end = src.indexOf("**", i + 2)
                if (end > i) {
                    take(i + 2, end) { InlineSpan.Bold(it) }
                    i = end + 2
                    continue
                }
                buf.append(c); i++
            }

            src.startsWith("~~", i) -> {
                val end = src.indexOf("~~", i + 2)
                if (end > i) {
                    take(i + 2, end) { InlineSpan.Strike(it) }
                    i = end + 2
                    continue
                }
                buf.append(c); i++
            }

            c == '*' || c == '_' -> {
                val end = src.indexOf(c, i + 1)
                // `*` may open mid-word; `_` may not, or every snake_case
                // identifier in a code-heavy transcript turns into italics.
                val leftFlank = c == '*' || i == 0 || src[i - 1].isWhitespace()
                if (end > i + 1 && leftFlank &&
                    !src[i + 1].isWhitespace() && !src[end - 1].isWhitespace()
                ) {
                    take(i + 1, end) { InlineSpan.Italic(it) }
                    i = end + 1
                    continue
                }
                buf.append(c); i++
            }

            c == '[' -> {
                val close = src.indexOf(']', i + 1)
                if (close > i && close + 1 < src.length && src[close + 1] == '(') {
                    val paren = src.indexOf(')', close + 2)
                    if (paren > close) {
                        take(i + 1, close) { InlineSpan.Link(it, src.substring(close + 2, paren)) }
                        i = paren + 1
                        continue
                    }
                }
                buf.append(c); i++
            }

            else -> {
                buf.append(c); i++
            }
        }
    }
    flush()
    return out
}

internal sealed interface InlineSpan {
    val text: String

    data class Plain(override val text: String) : InlineSpan
    data class Bold(override val text: String) : InlineSpan
    data class Italic(override val text: String) : InlineSpan
    data class Strike(override val text: String) : InlineSpan
    data class Code(override val text: String) : InlineSpan
    data class Link(override val text: String, val href: String) : InlineSpan
}