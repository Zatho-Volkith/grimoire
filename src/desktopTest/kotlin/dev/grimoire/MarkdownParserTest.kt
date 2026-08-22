package dev.grimoire

import dev.grimoire.work.Markdown
import dev.grimoire.work.MdBlock
import dev.grimoire.work.MdRun
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
}
