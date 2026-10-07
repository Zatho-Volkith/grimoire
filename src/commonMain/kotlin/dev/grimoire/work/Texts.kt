package dev.grimoire.work

/** One text-message conversation: a `::: texts` block in chapter Markdown. */
data class TextChat(val title: String, val owner: String?, val items: List<TextItem>) {
    /** More than one person besides the phone's owner is talking — received bubbles get names. */
    val isGroup: Boolean
        get() = items.mapNotNullTo(HashSet()) { (it as? TextItem.Message)?.sender }.count { it != owner } > 1
}

sealed interface TextItem {
    /** [text] is the raw Markdown source of the bubble; real `\n`s are line breaks within it. */
    data class Message(val sender: String?, val text: String) : TextItem
    data class Receipt(val text: String) : TextItem
    data class Notice(val text: String) : TextItem
}

/**
 * The `::: texts` block grammar, one item per line:
 *
 * ```
 * ::: texts Alex          chat title (contact or group name)
 * @me Sam                 whose phone it is — their messages are "sent"
 * Sam: you up?            message (sender ≤ 40 chars, before the first ": ")
 * ~ Read 2:15 AM          receipt under the item above
 * -- Alex added Jordan    centred system notice
 * :::
 * ```
 *
 * Anything else is a sender-less message, never an error; a leading `\` forces that reading.
 * Inside message text `\n` is a line break and `\\` a literal backslash.
 */
object Texts {

    private const val MAX_NAME = 40

    /** The chat title if [line] opens a texts block (`""` when untitled), else null. */
    fun fenceTitle(line: String): String? {
        val t = line.trim()
        if (!t.startsWith(":::")) return null
        val rest = t.removePrefix(":::").trimStart()
        if (!rest.startsWith("texts")) return null
        val after = rest.removePrefix("texts")
        if (after.isNotEmpty() && !after.first().isWhitespace()) return null
        return after.trim()
    }

    fun isCloseFence(line: String): Boolean = line.trim() == ":::"

    /** Parses the lines between the fences of a block whose fence carried [title]. */
    fun parse(title: String, lines: List<String>): TextChat {
        var owner: String? = null
        val items = mutableListOf<TextItem>()
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("@me ") -> owner = line.removePrefix("@me ").trim()
                else -> items += item(line)
            }
        }
        return TextChat(title, owner, items)
    }

    private fun item(line: String): TextItem = when {
        line.startsWith("\\") -> TextItem.Message(null, decode(line.drop(1)))
        line.startsWith("~ ") -> TextItem.Receipt(line.removePrefix("~ ").trim())
        line.startsWith("-- ") -> TextItem.Notice(line.removePrefix("-- ").trim())
        else -> {
            val colon = line.indexOf(": ")
            val name = if (colon > 0) line.substring(0, colon).trim() else ""
            if (name.isNotEmpty() && name.length <= MAX_NAME) TextItem.Message(name, decode(line.substring(colon + 2).trim()))
            else TextItem.Message(null, decode(line))
        }
    }

    /** The whole block, fences included, `\n`-joined with no trailing newline. */
    fun serialize(chat: TextChat): String = buildList {
        add(if (chat.title.isBlank()) "::: texts" else "::: texts ${chat.title.trim()}")
        chat.owner?.takeIf { it.isNotBlank() }?.let { add("@me ${it.trim()}") }
        chat.items.forEach { add(line(it)) }
        add(":::")
    }.joinToString("\n")

    private fun line(item: TextItem): String = when (item) {
        is TextItem.Receipt -> "~ ${item.text}"
        is TextItem.Notice -> "-- ${item.text}"
        is TextItem.Message -> {
            val body = encode(item.text)
            when {
                item.sender != null -> "${item.sender}: $body"
                // Text that would read back as anything but itself gets the forcing backslash.
                isCloseFence(body) || body.startsWith("@me ") ||
                    item(body) != TextItem.Message(null, decode(body)) -> "\\$body"
                else -> body
            }
        }
    }

    /**
     * The character range of the texts block containing [cursor] in the raw chapter [body]
     * (fence line start through the end of the closing `:::`, or the body's end when unclosed),
     * or null when the cursor is outside every block. Cursor positions on either fence count.
     */
    fun blockRangeAt(body: String, cursor: Int): IntRange? {
        var openStart = -1
        var lineStart = 0
        while (lineStart <= body.length) {
            val lineEnd = body.indexOf('\n', lineStart).let { if (it < 0) body.length else it }
            val line = body.substring(lineStart, lineEnd).trimEnd('\r')
            if (openStart < 0) {
                if (fenceTitle(line) != null) openStart = lineStart
            } else if (isCloseFence(line)) {
                val end = lineStart + line.length
                if (cursor in openStart..end) return openStart until end
                openStart = -1
            }
            lineStart = lineEnd + 1
        }
        return if (openStart >= 0 && cursor >= openStart) openStart until body.length else null
    }

    /**
     * Puts [block] into [body]: over [range] when editing an existing block, otherwise at
     * [cursor] with blank lines around it so it parses as its own block. Returns the new body
     * and the cursor position just after the block.
     */
    fun splice(body: String, range: IntRange?, cursor: Int, block: String): Pair<String, Int> {
        if (range != null) {
            val before = body.substring(0, range.first)
            return before + block + body.substring(range.last + 1) to before.length + block.length
        }
        val before = body.substring(0, cursor)
        val after = body.substring(cursor)
        val lead = when {
            before.isEmpty() || before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val trail = when {
            after.isEmpty() || after.startsWith("\n\n") -> ""
            after.startsWith("\n") -> "\n"
            else -> "\n\n"
        }
        return before + lead + block + trail + after to before.length + lead.length + block.length
    }

    /** `\\` stays (the inline parser shows it as one backslash); `\n` becomes a line break. */
    private fun decode(s: String): String {
        if ('\\' !in s) return s
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)
            when {
                c == '\\' && next == '\\' -> { out.append("\\\\"); i += 2 }
                c == '\\' && next == 'n' -> { out.append('\n'); i += 2 }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /**
     * Inverse of [decode]. A lone backslash that would otherwise merge with what follows
     * (`\n` typed literally, or a `\` right before a line break) is written as `\\`, which
     * displays the same.
     */
    private fun encode(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)
            when {
                c == '\\' && next == '\\' -> { out.append("\\\\"); i += 2 }
                c == '\\' && (next == 'n' || next == '\n' || next == null) -> { out.append("\\\\"); i++ }
                c == '\n' -> { out.append("\\n"); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
