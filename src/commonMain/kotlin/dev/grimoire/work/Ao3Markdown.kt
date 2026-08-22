package dev.grimoire.work

/**
 * Renders parsed chapter Markdown ([Markdown.parse]) as the limited HTML AO3's work text
 * accepts: `p`, `br`, `h1`–`h6`, `em`, `strong`, `s`, `blockquote`, `hr`, `a`, `ul`/`ol`/`li`.
 * Raw HTML in the source is escaped, never passed through, so pasting the output can't break.
 */
object Ao3Markdown {

    fun toHtml(markdown: String): String =
        Markdown.parse(markdown).joinToString("\n") { block(it) }

    private fun block(b: MdBlock): String = when (b) {
        is MdBlock.Heading -> "<h${b.level}>${inline(b.text)}</h${b.level}>"
        is MdBlock.Paragraph -> "<p>${b.lines.joinToString("<br />") { inline(it) }}</p>"
        is MdBlock.Quote -> "<blockquote>${b.blocks.joinToString("\n") { block(it) }}</blockquote>"
        is MdBlock.Listing -> {
            val tag = if (b.ordered) "ol" else "ul"
            "<$tag>\n${b.items.joinToString("\n") { "<li>${inline(it)}</li>" }}\n</$tag>"
        }
        MdBlock.Hr -> "<hr />"
    }

    private fun inline(runs: List<MdRun>): String = runs.joinToString("") { r ->
        var t = escape(r.text)
        if (r.strike) t = "<s>$t</s>"
        if (r.italic) t = "<em>$t</em>"
        if (r.bold) t = "<strong>$t</strong>"
        r.link?.let { url -> t = "<a href=\"${escape(url)}\">$t</a>" }
        t
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
