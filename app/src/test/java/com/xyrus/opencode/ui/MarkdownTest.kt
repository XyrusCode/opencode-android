package com.xyrus.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The markdown grammar, pinned. These cover the shapes that were rendering as
 * raw text before: headings, nested lists, task lists, quotes, rules and fences.
 */
class MarkdownTest {

    @Test
    fun `blank lines split paragraphs`() {
        val blocks = parseMarkdown("first para\n\nsecond para")
        assertEquals(2, blocks.size)
        assertEquals("first para", (blocks[0] as Block.Paragraph).text)
        assertEquals("second para", (blocks[1] as Block.Paragraph).text)
    }

    @Test
    fun `consecutive lines stay one paragraph`() {
        val blocks = parseMarkdown("line one\nline two")
        assertEquals(1, blocks.size)
        assertEquals("line one\nline two", (blocks[0] as Block.Paragraph).text)
    }

    @Test
    fun `headings keep their level`() {
        val blocks = parseMarkdown("### Third level")
        val heading = blocks.single() as Block.Heading
        assertEquals(3, heading.level)
        assertEquals("Third level", heading.text)
    }

    @Test
    fun `hash without a space is not a heading`() {
        // `#1` is an issue reference, not a heading.
        assertTrue(parseMarkdown("#1 fix the thing").single() is Block.Paragraph)
    }

    @Test
    fun `bullets keep nesting depth`() {
        val blocks = parseMarkdown("- top\n  - nested")
        assertEquals(2, blocks.size)
        assertEquals(0, (blocks[0] as Block.Bullet).indent)
        assertEquals(2, (blocks[1] as Block.Bullet).indent)
        assertEquals("nested", (blocks[1] as Block.Bullet).text)
    }

    @Test
    fun `ordered lists keep their number`() {
        val bullet = parseMarkdown("1. first").single() as Block.Bullet
        assertEquals("1.", bullet.marker)
        assertEquals("first", bullet.text)
    }

    @Test
    fun `task list items record done state`() {
        val done = parseMarkdown("- [x] shipped").single() as Block.Bullet
        assertEquals(true, done.checked)
        assertEquals("shipped", done.text)

        val todo = parseMarkdown("- [ ] pending").single() as Block.Bullet
        assertEquals(false, todo.checked)
    }

    @Test
    fun `horizontal rule is a rule not a bullet`() {
        assertEquals(Block.Rule, parseMarkdown("---").single())
        assertEquals(Block.Rule, parseMarkdown("***").single())
    }

    @Test
    fun `fenced code keeps language and body`() {
        val code = parseMarkdown("```kotlin\nval x = 1\n```").single() as Block.Code
        assertEquals("kotlin", code.lang)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun `code fence inside a list does not leak back into markdown`() {
        val blocks = parseMarkdown("- run this:\n\n```sh\nls -la\n```")
        assertTrue(blocks[0] is Block.Bullet)
        val code = blocks[1] as Block.Code
        assertEquals("ls -la", code.code)
    }

    @Test
    fun `unterminated fence still renders as code`() {
        // The model was cut off mid-block; showing nothing would hide the answer.
        val code = parseMarkdown("```py\nprint(1)").single() as Block.Code
        assertEquals("print(1)", code.code)
    }

    @Test
    fun `blockquote groups consecutive markers`() {
        val quote = parseMarkdown("> first\n> second").single() as Block.Quote
        assertEquals("first\nsecond", quote.text)
    }

    @Test
    fun `blockquotes do not swallow following paragraphs`() {
        val blocks = parseMarkdown("> quoted\nafter")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is Block.Quote)
        assertEquals("after", (blocks[1] as Block.Paragraph).text)
    }

    @Test
    fun `windows line endings do not produce stray paragraphs`() {
        val blocks = parseMarkdown("a\r\n\r\nb")
        assertEquals(2, blocks.size)
        assertEquals("a", (blocks[0] as Block.Paragraph).text)
    }

    // --- inline ---------------------------------------------------------

    @Test
    fun `bold and italic and strike split out`() {
        assertEquals(
            listOf(
                InlineSpan.Plain("a "),
                InlineSpan.Bold("b"),
                InlineSpan.Plain(" "),
                InlineSpan.Italic("c"),
                InlineSpan.Plain(" "),
                InlineSpan.Strike("d"),
            ),
            inlineSpans("a **b** *c* ~~d~~"),
        )
    }

    @Test
    fun `code span contents are not rescanned`() {
        assertEquals(
            listOf(InlineSpan.Plain("use "), InlineSpan.Code("**not bold**")),
            inlineSpans("use `**not bold**`"),
        )
    }

    @Test
    fun `snake case identifiers do not become italic`() {
        assertEquals(listOf(InlineSpan.Plain("some_field_name")), inlineSpans("some_field_name"))
    }

    @Test
    fun `underscore emphasis still works at a word boundary`() {
        assertEquals(
            listOf(InlineSpan.Italic("word"), InlineSpan.Plain(" here")),
            inlineSpans("_word_ here"),
        )
    }

    @Test
    fun `lone asterisk stays literal`() {
        assertEquals(listOf(InlineSpan.Plain("2 * 3")), inlineSpans("2 * 3"))
    }

    @Test
    fun `link shows its label not its url`() {
        assertEquals(
            listOf(InlineSpan.Link("docs", "https://example.com/x")),
            inlineSpans("[docs](https://example.com/x)"),
        )
    }

    @Test
    fun `unbalanced markers survive intact`() {
        assertEquals(listOf(InlineSpan.Plain("a ** b")), inlineSpans("a ** b"))
    }

    @Test
    fun `plain rendering round trips the visible text`() {
        val plain = markdownToPlain("## Title\n\n- one\n- two")
        assertEquals("Title\n• one\n• two", plain)
    }
}