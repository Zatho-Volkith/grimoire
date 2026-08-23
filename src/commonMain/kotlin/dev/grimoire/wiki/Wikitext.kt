package dev.grimoire.wiki

/**
 * Bounded wikitext -> Markdown conversion for wiki page bodies, plus infobox extraction.
 * Not a MediaWiki renderer — it keeps what prose needs (headings, lists, quotes, links,
 * emphasis) and drops the machinery (templates, files, refs, galleries, categories).
 */
object Wikitext {

    /** The page body as the Markdown our editor/preview already speak. */
    fun toMarkdown(wikitext: String): String {
        var s = wikitext.replace("\r\n", "\n")
        s = comments.replace(s, "")
        s = galleries.replace(s, "")
        s = refs.replace(s, "")
        s = refSelfClosed.replace(s, "")
        s = breaks.replace(s, "\n")
        s = transformTemplates(s)
        s = removeBracketedJunk(s)
        s = wikiLinkLabeled.replace(s) { it.groupValues[2] }
        s = wikiLinkBare.replace(s) { it.groupValues[1].substringAfterLast(':') }
        s = externalLabeled.replace(s) { "[${it.groupValues[2]}](${it.groupValues[1]})" }
        s = s.replace("'''''", "***").replace("'''", "**").replace("''", "*")
        s = s.lines().joinToString("\n") { line ->
            val trimmed = line.trimEnd()
            when {
                headingPattern.matches(trimmed) -> {
                    val m = headingPattern.matchEntire(trimmed)!!
                    "#".repeat(m.groupValues[1].length.coerceAtMost(6)) + " " + m.groupValues[2].trim()
                }
                trimmed.startsWith("*") -> "- " + trimmed.trimStart('*', ' ')
                trimmed.startsWith("#") && !trimmed.startsWith("# ") && headingSafe(trimmed) -> "1. " + trimmed.trimStart('#', ' ')
                else -> trimmed
            }
        }
        return s.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** First plain paragraph of [markdown] — the intro sentence(s), for `entity.summary`. */
    fun firstParagraph(markdown: String): String =
        markdown.split("\n\n").firstOrNull { block ->
            val t = block.trim()
            t.isNotEmpty() && !t.startsWith("#") && !t.startsWith(">") && !t.startsWith("-") &&
                !t.startsWith("1.") && !t.startsWith("---")
        }?.trim()?.replace('\n', ' ').orEmpty()

    /**
     * Infobox parameters as an attribute map. Handles the paired-row style
     * (`Row 1 title` = "Power/Ability to:", `Row 1 info` = "...") used by Fandom infoboxes;
     * presentation-only keys (image, colors, captions) are dropped.
     */
    fun infobox(wikitext: String): Map<String, String> {
        val box = topLevelTemplates(wikitext)
            .firstOrNull { it.name.startsWith("infobox", ignoreCase = true) } ?: return emptyMap()
        val params = box.params.mapNotNull { p ->
            val eq = topLevelIndexOf(p, '=')
            if (eq < 0) null else p.take(eq).trim() to p.drop(eq + 1).trim()
        }.toMap()

        val titles = Regex("""Row (\d+) title""", RegexOption.IGNORE_CASE)
        val result = linkedMapOf<String, String>()
        for ((key, value) in params) {
            val m = titles.matchEntire(key) ?: continue
            val info = params.entries.firstOrNull { it.key.equals("Row ${m.groupValues[1]} info", ignoreCase = true) }?.value ?: continue
            val cleanKey = inline(value).trimEnd(':').trim()
            val cleanValue = inline(info)
            if (cleanKey.isNotEmpty() && cleanValue.isNotEmpty()) result[cleanKey] = cleanValue
        }
        return result
    }

    // --- internals ---

    private val comments = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val galleries = Regex("""<gallery.*?</gallery>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val refs = Regex("""<ref.*?</ref>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val refSelfClosed = Regex("""<ref[^>]*/>""", RegexOption.IGNORE_CASE)
    private val breaks = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val wikiLinkLabeled = Regex("""\[\[([^\[\]|]*)\|([^\[\]]*)]]""")
    private val wikiLinkBare = Regex("""\[\[([^\[\]]*)]]""")
    private val externalLabeled = Regex("""\[(https?://\S+) ([^\]]+)]""")
    private val headingPattern = Regex("""(={2,6})\s*(.*?)\s*={2,6}""")

    private fun headingSafe(line: String): Boolean = !line.startsWith("##")

    private data class Template(val range: IntRange, val name: String, val params: List<String>)

    /** Top-level `{{...}}` blocks with their params split at depth zero. */
    private fun topLevelTemplates(s: String): List<Template> {
        val out = mutableListOf<Template>()
        var i = 0
        while (i < s.length - 1) {
            if (s[i] == '{' && s[i + 1] == '{') {
                var depth = 0
                var j = i
                while (j < s.length - 1) {
                    when {
                        s[j] == '{' && s[j + 1] == '{' -> { depth++; j += 2 }
                        s[j] == '}' && s[j + 1] == '}' -> { depth--; j += 2; if (depth == 0) break }
                        else -> j++
                    }
                }
                val body = s.substring(i + 2, (j - 2).coerceAtLeast(i + 2))
                val parts = splitTopLevel(body)
                out += Template(i until j, parts.firstOrNull()?.trim().orEmpty(), parts.drop(1))
                i = j
            } else i++
        }
        return out
    }

    /** Splits template content on `|` ignoring nested `{{ }}` and `[[ ]]`. */
    private fun splitTopLevel(s: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var brace = 0
        var bracket = 0
        var i = 0
        while (i < s.length) {
            when {
                i < s.length - 1 && s[i] == '{' && s[i + 1] == '{' -> { brace++; current.append("{{"); i += 2 }
                i < s.length - 1 && s[i] == '}' && s[i + 1] == '}' -> { brace--; current.append("}}"); i += 2 }
                i < s.length - 1 && s[i] == '[' && s[i + 1] == '[' -> { bracket++; current.append("[["); i += 2 }
                i < s.length - 1 && s[i] == ']' && s[i + 1] == ']' -> { bracket--; current.append("]]"); i += 2 }
                s[i] == '|' && brace == 0 && bracket == 0 -> { parts += current.toString(); current.clear(); i++ }
                else -> { current.append(s[i]); i++ }
            }
        }
        parts += current.toString()
        return parts
    }

    private fun topLevelIndexOf(s: String, target: Char): Int {
        var brace = 0; var bracket = 0
        var i = 0
        while (i < s.length) {
            when {
                i < s.length - 1 && s[i] == '{' && s[i + 1] == '{' -> { brace++; i += 2 }
                i < s.length - 1 && s[i] == '}' && s[i + 1] == '}' -> { brace--; i += 2 }
                i < s.length - 1 && s[i] == '[' && s[i + 1] == '[' -> { bracket++; i += 2 }
                i < s.length - 1 && s[i] == ']' && s[i + 1] == ']' -> { bracket--; i += 2 }
                s[i] == target && brace == 0 && bracket == 0 -> return i
                else -> i++
            }
        }
        return -1
    }

    /** Quote templates become blockquotes; every other top-level template is dropped. */
    private fun transformTemplates(s: String): String {
        val templates = topLevelTemplates(s)
        if (templates.isEmpty()) return s
        val out = StringBuilder()
        var last = 0
        for (t in templates) {
            out.append(s, last, t.range.first)
            if (t.name.equals("quote", ignoreCase = true) && t.params.isNotEmpty()) {
                val text = t.params[0].trim()
                val attribution = t.params.getOrNull(1)?.trim()
                out.append("> ").append(text)
                if (!attribution.isNullOrEmpty()) out.append("\n> — ").append(attribution)
                out.append('\n')
            }
            last = t.range.last + 1
        }
        out.append(s, last, s.length)
        // Templates can nest; a second pass catches ones that were inside removed blocks.
        return if (topLevelTemplates(out.toString()).isEmpty()) out.toString() else transformTemplates(out.toString())
    }

    /** `[[File:...]]`, `[[Image:...]]`, `[[Category:...]]` — balanced, captions may nest links. */
    private fun removeBracketedJunk(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (i < s.length - 1 && s[i] == '[' && s[i + 1] == '[') {
                val rest = s.substring(i + 2)
                val isJunk = listOf("file:", "image:", "category:").any { rest.startsWith(it, ignoreCase = true) }
                if (isJunk) {
                    var depth = 0
                    var j = i
                    while (j < s.length - 1) {
                        when {
                            s[j] == '[' && s[j + 1] == '[' -> { depth++; j += 2 }
                            s[j] == ']' && s[j + 1] == ']' -> { depth--; j += 2; if (depth == 0) break }
                            else -> j++
                        }
                    }
                    i = j
                    continue
                }
            }
            out.append(s[i]); i++
        }
        return out.toString()
    }

    /** Inline-only cleaning for infobox values: links, emphasis, breaks — no block structure. */
    private fun inline(s: String): String {
        var v = breaks.replace(s, "; ")
        v = transformTemplates(v)
        v = wikiLinkLabeled.replace(v) { it.groupValues[2] }
        v = wikiLinkBare.replace(v) { it.groupValues[1].substringAfterLast(':') }
        v = externalLabeled.replace(v) { it.groupValues[2] }
        v = v.replace("'''''", "").replace("'''", "").replace("''", "")
        return v.replace(Regex("\\s+"), " ").trim()
    }
}
