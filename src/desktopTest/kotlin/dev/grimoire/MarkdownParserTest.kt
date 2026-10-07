package dev.grimoire

import dev.grimoire.work.Markdown
import dev.grimoire.work.MdBlock
import dev.grimoire.work.MdRun
import dev.grimoire.work.TextChat
import dev.grimoire.work.TextItem
import kotlin.test.Test
import kotlin.test.assertEquals

/** Model-level checks; the HTML behavior on top of this parser is pinned by [Ao3MarkdownTest]. */
class MarkdownParserTest {

    @Test
    fun `scene breaks parse to Hr blocks`() {
        assertEquals(listOf(MdBlock.Hr), Markdown.parse("---"))
    }

    @Test
    fun `nested emphasis lands on a single styled run`() {
        assertEquals(listOf(MdRun("c", bold = true, italic = true)), Markdown.parseInline("***c***"))
        assertEquals(
            listOf(MdRun("bold", bold = true), MdRun(" and "), MdRun("it", italic = true)),
            Markdown.parseInline("**bold** and *it*"),
        )
    }

    @Test
    fun `links carry their url on the run`() {
        assertEquals(
            listOf(MdRun("see "), MdRun("map", link = "https://x.y/z")),
            Markdown.parseInline("see [map](https://x.y/z)"),
        )
    }

    @Test
    fun `quotes contain their own parsed blocks`() {
        val quote = Markdown.parse("> line one\n> \n> line two").single() as MdBlock.Quote
        assertEquals(2, quote.blocks.size) // the bare `>` splits two paragraphs
    }

    @Test
    fun `texts block is its own block`() {
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(listOf(MdRun("Before.")))),
                MdBlock.Texts(TextChat("Alex", null, listOf(TextItem.Message("Alex", "hi")))),
                MdBlock.Paragraph(listOf(listOf(MdRun("After.")))),
            ),
            Markdown.parse("Before.\n::: texts Alex\nAlex: hi\n:::\nAfter."),
        )
    }

    @Test
    fun `unclosed texts block runs to the end`() {
        val block = Markdown.parse("::: texts\nA: x\n\nB: y").single() as MdBlock.Texts
        assertEquals(listOf(TextItem.Message("A", "x"), TextItem.Message("B", "y")), block.chat.items)
    }

    @Test
    fun `other colon fences stay prose`() {
        assertEquals(
            listOf(MdBlock.Paragraph(listOf(listOf(MdRun(":::note")), listOf(MdRun("hello"))))),
            Markdown.parse(":::note\nhello"),
        )
    }
}
