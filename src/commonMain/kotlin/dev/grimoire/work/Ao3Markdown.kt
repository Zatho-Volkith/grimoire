package dev.grimoire.work

/**
 * Renders parsed chapter Markdown ([Markdown.parse]) as the limited HTML AO3's work text
 * accepts: `p`, `br`, `h1`–`h6`, `em`, `strong`, `s`, `blockquote`, `hr`, `a`, `ul`/`ol`/`li`,
 * plus class-tagged `div`/`p`/`span` for text conversations.
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
        is MdBlock.Texts -> chat(b.chat)
    }

    /**
     * A conversation as class-tagged paragraphs for the Work Skin ([Ao3WorkSkin]) to style.
     * Sender names stay in the text so it still reads as a chat log with creator styles off.
     */
    private fun chat(c: TextChat): String = buildList {
        add(if (c.isGroup) "<div class=\"gt-chat gt-group\">" else "<div class=\"gt-chat\">")
        if (c.title.isNotBlank()) add("<p class=\"gt-header\">${escape(c.title)}</p>")
        c.items.forEach { item ->
            add(
                when (item) {
                    is TextItem.Message -> {
                        val side = if (item.sender != null && item.sender == c.owner) "gt-me" else "gt-them"
                        val name = item.sender?.let { "<span class=\"gt-name\">${escape(it)}:</span> " }.orEmpty()
                        val body = item.text.split('\n').joinToString("<br />") { inline(Markdown.parseInline(it)) }
                        "<p class=\"gt-msg $side\">$name$body</p>"
                    }
                    is TextItem.Receipt -> "<p class=\"gt-receipt\">${escape(item.text)}</p>"
                    is TextItem.Notice -> "<p class=\"gt-notice\"><em>${escape(item.text)}</em></p>"
                },
            )
        }
        add("</div>")
    }.joinToString("\n")

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
