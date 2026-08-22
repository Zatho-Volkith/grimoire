package dev.grimoire.work

/**
 * Converts the prose subset of Markdown — what chapter drafts use, and what Obsidian renders —
 * into the limited HTML that AO3's work text accepts: `p`, `br`, `h1`–`h6`, `em`, `strong`, `s`,
 * `blockquote`, `hr`, `a`, `ul`/`ol`/`li`.
 *
 * Deliberately small and predictable:
 *  - Raw HTML in the source is escaped, never passed through, so pasting the output can't break.
 *  - A single newline inside a paragraph becomes `<br />` (Obsidian's default reading).
 *  - Code blocks, tables, footnotes, nested lists are out of scope for fiction prose.
 */
object Ao3Markdown {

    fun toHtml(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            when {
                trimmed.isEmpty() -> i++

                hrPattern.matches(trimmed) -> { blocks += "<hr />"; i++ }

                headingLevel(trimmed) > 0 -> {
                    val level = headingLevel(trimmed)
                    blocks += "<h$level>${inline(trimmed.drop(level).trim())}</h$level>"
                    i++
                }

                trimmed.startsWith(">") -> {
                    val inner = StringBuilder()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        inner.append(lines[i].trim().removePrefix(">").removePrefix(" ")).append('\n')
                        i++
                    }
                    blocks += "<blockquote>${toHtml(inner.toString())}</blockquote>"
                }

                bulletPattern.matches(trimmed) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && bulletPattern.matches(lines[i].trim())) {
                        items += "<li>${inline(bulletPattern.matchEntire(lines[i].trim())!!.groupValues[1])}</li>"
                        i++
                    }
                    blocks += "<ul>\n${items.joinToString("\n")}\n</ul>"
                }

                orderedPattern.matches(trimmed) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && orderedPattern.matches(lines[i].trim())) {
                        items += "<li>${inline(orderedPattern.matchEntire(lines[i].trim())!!.groupValues[1])}</li>"
                        i++
                    }
                    blocks += "<ol>\n${items.joinToString("\n")}\n</ol>"
                }

                else -> {
                    val para = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().let {
                            it.isNotEmpty() && !hrPattern.matches(it) && headingLevel(it) == 0 &&
                                !it.startsWith(">") && !bulletPattern.matches(it) && !orderedPattern.matches(it)
                        }
                    ) {
                        para += inline(lines[i].trim())
                        i++
                    }
                    blocks += "<p>${para.joinToString("<br />")}</p>"
                }
            }
        }
        return blocks.joinToString("\n")
    }

    // --- blocks ---

    private val hrPattern = Regex("""-{3,}|\*{3,}|_{3,}""")
    private val bulletPattern = Regex("""[-*+] (.+)""")
    private val orderedPattern = Regex("""\d+[.)] (.+)""")

    /** 1–6 for `# heading` lines (a space after the hashes is required), else 0. */
    private fun headingLevel(line: String): Int {
        val n = line.takeWhile { it == '#' }.length
        return if (n in 1..6 && line.getOrNull(n) == ' ') n else 0
    }

    // --- inline ---

    /**
     * Backslash-escapable Markdown characters, each paired with a control-character placeholder
     * (U+0001…) that hides it from the formatting regexes until the end of [inline].
     */
    private val escapes: List<Pair<Char, Char>> =
        "\\*_~[".mapIndexed { index, c -> c to (index + 1).toChar() }

    private val link = Regex("""\[([^\]]+)\]\(([^)\s]+)\)""")
    private val boldItalic = Regex("""\*\*\*(.+?)\*\*\*""")
    private val bold = Regex("""\*\*(.+?)\*\*""")
    private val italicStar = Regex("""\*([^*]+)\*""")
    private val italicUnderscore = Regex("""(?<!\w)_([^_]+)_(?!\w)""")
    private val strike = Regex("""~~(.+?)~~""")

    private fun inline(text: String): String {
        var s = text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        for ((char, placeholder) in escapes) s = s.replace("\\$char", placeholder.toString())
        s = link.replace(s) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        s = boldItalic.replace(s) { "<strong><em>${it.groupValues[1]}</em></strong>" }
        s = bold.replace(s) { "<strong>${it.groupValues[1]}</strong>" }
        s = italicStar.replace(s) { "<em>${it.groupValues[1]}</em>" }
        s = italicUnderscore.replace(s) { "<em>${it.groupValues[1]}</em>" }
        s = strike.replace(s) { "<s>${it.groupValues[1]}</s>" }
        for ((char, placeholder) in escapes) s = s.replace(placeholder.toString(), char.toString())
        return s
    }
}
