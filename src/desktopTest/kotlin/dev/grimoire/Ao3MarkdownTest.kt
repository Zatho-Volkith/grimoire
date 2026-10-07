package dev.grimoire

import dev.grimoire.work.Ao3Markdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Ao3MarkdownTest {

    @Test
    fun `paragraphs split on blank lines, single newlines become br`() {
        assertEquals(
            "<p>One.<br />Two.</p>\n<p>Three.</p>",
            Ao3Markdown.toHtml("One.\nTwo.\n\nThree."),
        )
    }

    @Test
    fun `emphasis in all its markdown spellings`() {
        assertEquals("<p><em>a</em> <strong>b</strong> <strong><em>c</em></strong> <em>d</em> <s>e</s></p>",
            Ao3Markdown.toHtml("*a* **b** ***c*** _d_ ~~e~~"))
    }

    @Test
    fun `underscores inside words are left alone`() {
        assertEquals("<p>snake_case_name stays</p>", Ao3Markdown.toHtml("snake_case_name stays"))
    }

    @Test
    fun `scene break becomes hr`() {
        assertEquals(
            "<p>Before.</p>\n<hr />\n<p>After.</p>",
            Ao3Markdown.toHtml("Before.\n\n---\n\nAfter."),
        )
    }

    @Test
    fun `headings need a space after the hashes`() {
        assertEquals("<h2>Interlude</h2>", Ao3Markdown.toHtml("## Interlude"))
        assertEquals("<p>#hashtag</p>", Ao3Markdown.toHtml("#hashtag"))
    }

    @Test
    fun `blockquotes wrap their own paragraphs`() {
        assertEquals(
            "<blockquote><p>Dear Rin,<br />don't wait.</p></blockquote>",
            Ao3Markdown.toHtml("> Dear Rin,\n> don't wait."),
        )
    }

    @Test
    fun `flat lists`() {
        assertEquals(
            "<ul>\n<li>swords</li>\n<li>rations</li>\n</ul>\n<ol>\n<li>leave</li>\n<li>return</li>\n</ol>",
            Ao3Markdown.toHtml("- swords\n- rations\n\n1. leave\n2. return"),
        )
    }

    @Test
    fun `links render as anchors`() {
        assertEquals(
            "<p>see <a href=\"https://example.com/a\">the map</a>.</p>",
            Ao3Markdown.toHtml("see [the map](https://example.com/a)."),
        )
    }

    @Test
    fun `raw html is escaped, never passed through`() {
        assertEquals(
            "<p>&lt;script&gt;alert(1)&lt;/script&gt; Tom &amp; Jerry</p>",
            Ao3Markdown.toHtml("<script>alert(1)</script> Tom & Jerry"),
        )
    }

    @Test
    fun `backslash escapes suppress formatting`() {
        assertEquals("<p>*not italic*</p>", Ao3Markdown.toHtml("""\*not italic\*"""))
    }

    @Test
    fun `empty input gives empty output`() {
        assertEquals("", Ao3Markdown.toHtml(""))
        assertEquals("", Ao3Markdown.toHtml("\n\n"))
    }

    private val chatSource = """
        ::: texts Alex
        @me Sam
        Sam: you up?
        Alex: unfortunately
        Sam: same
        ~ Read 2:15 AM
        -- Alex added Jordan to the chat
        :::
    """.trimIndent()

    @Test
    fun `one-on-one chat html`() {
        assertEquals(
            """
            <div class="gt-chat">
            <p class="gt-header">Alex</p>
            <p class="gt-msg gt-me"><span class="gt-name">Sam:</span> you up?</p>
            <p class="gt-msg gt-them"><span class="gt-name">Alex:</span> unfortunately</p>
            <p class="gt-msg gt-me"><span class="gt-name">Sam:</span> same</p>
            <p class="gt-receipt">Read 2:15 AM</p>
            <p class="gt-notice"><em>Alex added Jordan to the chat</em></p>
            </div>
            """.trimIndent(),
            Ao3Markdown.toHtml(chatSource),
        )
    }

    @Test
    fun `group chat html`() {
        val html = Ao3Markdown.toHtml(chatSource.replace("\n:::", "\nJordan: what did I miss\n:::"))
        assertTrue(html.startsWith("<div class=\"gt-chat gt-group\">\n"), html)
    }

    @Test
    fun `blank title and sender-less message`() {
        assertEquals(
            "<div class=\"gt-chat\">\n<p class=\"gt-msg gt-them\">hello &lt;b&gt; &amp; &quot;x&quot;</p>\n</div>",
            Ao3Markdown.toHtml("::: texts\nhello <b> & \"x\"\n:::"),
        )
    }

    @Test
    fun `inline formatting and breaks in a bubble`() {
        val html = Ao3Markdown.toHtml("::: texts\nA: *hi*\\nthere\n:::")
        assertTrue(html.contains("<span class=\"gt-name\">A:</span> <em>hi</em><br />there</p>"), html)
    }
}
