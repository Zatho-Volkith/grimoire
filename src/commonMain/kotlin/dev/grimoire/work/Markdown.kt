package dev.grimoire.work

/** One styled run of inline text. [link] carries the URL when the run is a link's visible text. */
data class MdRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

/** One block of parsed Markdown. Paragraph lines map to soft breaks. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: List<MdRun>) : MdBlock
    data class Paragraph(val lines: List<List<MdRun>>) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class Listing(val ordered: Boolean, val items: List<List<MdRun>>) : MdBlock
    data object Hr : MdBlock
    /** A `::: texts` conversation (see [Texts]). */
    data class Texts(val chat: TextChat) : MdBlock
}

/**
 * Parser for the prose subset of Markdown that chapter bodies use — what Obsidian renders:
 * paragraphs with soft line breaks, `# headings`, `::: texts` conversations, `> quotes`, `---` scene breaks, flat lists,
 * `*em*`/`**strong**`/`***both***`/`_em_`/`~~strike~~`, `[links](url)`, and backslash escapes.
 *
 * One model, two consumers — [Ao3Markdown] renders it as AO3's allowed HTML and the editor's
 * live preview renders it as Compose — so what you see is always what AO3 gets.
 */
object Markdown {

    fun parse(markdown: String): List<MdBlock> {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            when {
                trimmed.isEmpty() -> i++

                Texts.fenceTitle(trimmed) != null -> {
                    val title = Texts.fenceTitle(trimmed)!!
                    val inner = mutableListOf<String>()
                    i++
                    while (i < lines.size && !Texts.isCloseFence(lines[i])) inner += lines[i++]
                    i++ // the closing fence, if any
                    blocks += MdBlock.Texts(Texts.parse(title, inner))
                }

                hrPattern.matches(trimmed) -> { blocks += MdBlock.Hr; i++ }

                headingLevel(trimmed) > 0 -> {
                    val level = headingLevel(trimmed)
                    blocks += MdBlock.Heading(level, parseInline(trimmed.drop(level).trim()))
                    i++
                }

                trimmed.startsWith(">") -> {
                    val inner = StringBuilder()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        inner.append(lines[i].trim().removePrefix(">").removePrefix(" ")).append('\n')
                        i++
                    }
                    blocks += MdBlock.Quote(parse(inner.toString()))
                }

                bulletPattern.matches(trimmed) -> {
                    val items = mutableListOf<List<MdRun>>()
                    while (i < lines.size && bulletPattern.matches(lines[i].trim())) {
                        items += parseInline(bulletPattern.matchEntire(lines[i].trim())!!.groupValues[1])
                        i++
                    }
                    blocks += MdBlock.Listing(ordered = false, items = items)
                }

                orderedPattern.matches(trimmed) -> {
                    val items = mutableListOf<List<MdRun>>()
                    while (i < lines.size && orderedPattern.matches(lines[i].trim())) {
                        items += parseInline(orderedPattern.matchEntire(lines[i].trim())!!.groupValues[1])
                        i++
                    }
                    blocks += MdBlock.Listing(ordered = true, items = items)
                }

                else -> {
                    val para = mutableListOf<List<MdRun>>()
                    while (i < lines.size && lines[i].trim().let {
                            it.isNotEmpty() && !hrPattern.matches(it) && headingLevel(it) == 0 &&
                                Texts.fenceTitle(it) == null &&
                                !it.startsWith(">") && !bulletPattern.matches(it) && !orderedPattern.matches(it)
                        }
                    ) {
                        para += parseInline(lines[i].trim())
                        i++
                    }
                    blocks += MdBlock.Paragraph(para)
                }
            }
        }
        return blocks
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
     * Backslash-escapable Markdown characters, each hidden behind a control-character
     * placeholder (U+0001…) while the patterns run, then restored as literals per run.
     */
    private val escapes: List<Pair<Char, Char>> =
        "\\*_~[".mapIndexed { index, c -> c to (index + 1).toChar() }

    private val link = Regex("""\[([^\]]+)\]\(([^)\s]+)\)""")
    private val boldItalic = Regex("""\*\*\*(.+?)\*\*\*""")
    private val bold = Regex("""\*\*(.+?)\*\*""")
    private val italicStar = Regex("""\*([^*]+)\*""")
    private val italicUnderscore = Regex("""(?<!\w)_([^_]+)_(?!\w)""")
    private val strike = Regex("""~~(.+?)~~""")

    /** Earliest match wins; listing order breaks ties (so `***` beats `**` beats `*`). */
    private val patterns = listOf(link, boldItalic, bold, italicStar, italicUnderscore, strike)

    fun parseInline(text: String): List<MdRun> {
        var s = text
        for ((char, placeholder) in escapes) s = s.replace("\\$char", placeholder.toString())
        return inline(s, MdRun(""))
    }

    private fun inline(s: String, style: MdRun): List<MdRun> {
        if (s.isEmpty()) return emptyList()
        val (pattern, match) = patterns
            .mapNotNull { p -> p.find(s)?.let { p to it } }
            .minByOrNull { (p, m) -> m.range.first * patterns.size + patterns.indexOf(p) }
            ?: return listOf(literal(s, style))

        val result = mutableListOf<MdRun>()
        if (match.range.first > 0) result += literal(s.substring(0, match.range.first), style)
        val inner = match.groupValues[1]
        result += when (pattern) {
            link -> inline(inner, style.copy(link = restore(match.groupValues[2])))
            boldItalic -> inline(inner, style.copy(bold = true, italic = true))
            bold -> inline(inner, style.copy(bold = true))
            strike -> inline(inner, style.copy(strike = true))
            else -> inline(inner, style.copy(italic = true))
        }
        result += inline(s.substring(match.range.last + 1), style)
        return result
    }

    private fun literal(s: String, style: MdRun): MdRun = style.copy(text = restore(s))

    private fun restore(s: String): String {
        var out = s
        for ((char, placeholder) in escapes) out = out.replace(placeholder.toString(), char.toString())
        return out
    }
}
