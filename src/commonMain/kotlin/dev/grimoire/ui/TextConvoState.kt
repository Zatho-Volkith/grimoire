package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.work.TextChat
import dev.grimoire.work.TextItem

enum class RowKind { MESSAGE, RECEIPT, NOTICE }

/** One line of the conversation being edited. [sender] only means something for messages. */
data class ConvoRow(val key: Int, val kind: RowKind, val sender: String, val text: String)

/**
 * The *Text convo* dialog's model: an editable list of rows that becomes a [TextChat].
 * Plain state holder — no Compose UI — so the editing rules are unit-testable.
 */
class TextConvoState(initial: TextChat?, private val castNames: List<String>) {
    var title by mutableStateOf(initial?.title.orEmpty()); private set
    var owner by mutableStateOf(initial?.owner.orEmpty()); private set

    private val _rows = mutableStateListOf<ConvoRow>()
    val rows: List<ConvoRow> get() = _rows
    private var nextKey = 0

    init {
        initial?.items?.forEach { item ->
            _rows += when (item) {
                is TextItem.Message -> ConvoRow(nextKey++, RowKind.MESSAGE, item.sender.orEmpty(), item.text)
                is TextItem.Receipt -> ConvoRow(nextKey++, RowKind.RECEIPT, "", item.text)
                is TextItem.Notice -> ConvoRow(nextKey++, RowKind.NOTICE, "", item.text)
            }
        }
    }

    fun editTitle(v: String) { title = v }
    fun editOwner(v: String) { owner = v }

    /**
     * Adds a message after [afterKey] (or at the end) and returns its key. The sender is
     * prefilled so a back-and-forth needs no picking: whoever spoke before the last speaker;
     * failing that, the contact (chat title) after the owner, else the owner.
     */
    fun addMessage(afterKey: Int? = null): Int {
        val at = insertionIndex(afterKey)
        // Blank rows are dropped on save, so they don't count as anyone having spoken.
        val speakers = _rows.subList(0, at).filter { it.kind == RowKind.MESSAGE && it.text.isNotBlank() }.map { name(it.sender) }
        val last = speakers.lastOrNull()?.takeIf { it.isNotEmpty() }
        val before = speakers.dropLast(1).lastOrNull()?.takeIf { it.isNotEmpty() }
        val me = name(owner)
        val sender = when {
            before != null && before != last -> before
            last == null -> me
            last == me -> name(title)
            else -> me
        }
        return insert(at, RowKind.MESSAGE, sender)
    }

    fun addReceipt(): Int = insert(_rows.size, RowKind.RECEIPT, "")
    fun addNotice(): Int = insert(_rows.size, RowKind.NOTICE, "")

    fun editText(key: Int, v: String) = update(key) { it.copy(text = v) }
    fun editSender(key: Int, v: String) = update(key) { it.copy(sender = v) }

    fun move(key: Int, delta: Int) {
        val from = _rows.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in _rows.indices) return
        _rows.add(to, _rows.removeAt(from))
    }

    fun remove(key: Int) { _rows.removeAll { it.key == key } }

    /** Owner, then this chat's senders in order of appearance, then the chapter's cast. */
    val senderSuggestions: List<String>
        get() = (listOf(owner) + _rows.filter { it.kind == RowKind.MESSAGE }.map { it.sender } + castNames)
            .map(::name).filter { it.isNotEmpty() }.distinct()

    /** The conversation to save, or null when every row is blank. */
    fun toChat(): TextChat? {
        val items = _rows.filter { it.text.isNotBlank() }.map { row ->
            when (row.kind) {
                RowKind.MESSAGE -> TextItem.Message(name(row.sender).ifEmpty { null }, row.text.trim())
                RowKind.RECEIPT -> TextItem.Receipt(oneLine(row.text))
                RowKind.NOTICE -> TextItem.Notice(oneLine(row.text))
            }
        }
        if (items.isEmpty()) return null
        return TextChat(oneLine(title), name(owner).ifEmpty { null }, items)
    }

    private fun insertionIndex(afterKey: Int?): Int =
        afterKey?.let { k -> _rows.indexOfFirst { it.key == k }.takeIf { it >= 0 }?.plus(1) } ?: _rows.size

    private fun insert(at: Int, kind: RowKind, sender: String): Int {
        val key = nextKey++
        _rows.add(at, ConvoRow(key, kind, sender, ""))
        return key
    }

    private fun update(key: Int, change: (ConvoRow) -> ConvoRow) {
        val i = _rows.indexOfFirst { it.key == key }
        if (i >= 0) _rows[i] = change(_rows[i])
    }

    private companion object {
        /**
         * A sender name the block grammar reads back unchanged: one line, no `": "`, no leading
         * marker characters (`-- x: hi` would read as a notice), ≤ 40 chars.
         */
        fun name(raw: String): String =
            oneLine(raw).replace(Regex(""":\s+"""), ":").trimStart('\\', '~', '-', '@', ':', ' ').take(40).trim()
        fun oneLine(raw: String): String = raw.replace('\n', ' ').trim()
    }
}
