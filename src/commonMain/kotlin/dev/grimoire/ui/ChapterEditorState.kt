package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.db.CastForChapter
import dev.grimoire.db.Entity
import dev.grimoire.entity.EntityRepository
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.grimoire.work.Ao3Markdown
import dev.grimoire.work.Markdown
import dev.grimoire.work.MdBlock
import dev.grimoire.work.Texts
import dev.grimoire.work.WorkRepository
import dev.grimoire.work.Words
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editing session for one chapter. Same debounce/flush contract as [EntityEditorState]:
 * edits land in local state immediately, are written after a quiet period, and [flush]
 * persists synchronously-in-suspend so navigation can never lose keystrokes.
 */
class ChapterEditorState(
    val id: String,
    private val repo: WorkRepository,
    private val entities: EntityRepository,
    private val worldId: String,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var title by mutableStateOf(""); private set
    var body by mutableStateOf(""); private set
    var dirty by mutableStateOf(false); private set
    /** Whether the live preview pane is shown beside the editor. UI-only; never saved. */
    var showPreview by mutableStateOf(true); private set

    /** Live word count of the unsaved body — what the status row shows while typing. */
    val wordCount: Long get() = Words.count(body)

    private var saveJob: Job? = null

    suspend fun load() {
        val c = repo.chapter(id).first() ?: return
        title = c.title.orEmpty()
        body = c.body
        loaded = true
    }

    fun editTitle(v: String) = edit { title = v }
    fun togglePreview() { showPreview = !showPreview }
    fun editBody(v: String) = edit { body = v }

    /** Where the cursor sits in [body] — what *Text convo* opens or inserts at. UI-only. */
    var selection by mutableStateOf(TextRange.Zero); private set

    private var composition by mutableStateOf<TextRange?>(null)

    /** What the body field shows: saved text plus cursor and any in-progress IME composition. */
    val bodyValue: TextFieldValue
        get() = TextFieldValue(body, selection.within(body), composition?.within(body))

    private fun TextRange.within(s: String) = TextRange(start.coerceIn(0, s.length), end.coerceIn(0, s.length))

    /** Body field change: the cursor always moves; only a text change counts as an edit. */
    fun editBodyValue(v: TextFieldValue) {
        selection = v.selection
        composition = v.composition
        if (v.text != body) editBody(v.text)
    }

    // --- text conversations: the dialog edits a copy; the body changes only on save.

    var textConvo by mutableStateOf<TextConvoState?>(null); private set
    private var textConvoRange: IntRange? = null

    /** Opens the dialog on the conversation under the cursor, or on a new one inserted there. */
    fun openTextConvo(castNames: List<String>) {
        val range = Texts.blockRangeAt(body, selection.start.coerceIn(0, body.length))
        val existing = range?.let { (Markdown.parse(body.substring(it)).firstOrNull() as? MdBlock.Texts)?.chat }
        textConvoRange = range
        textConvo = TextConvoState(existing, castNames)
    }

    /** An all-blank conversation saves nothing — an existing block is left as it was. */
    fun saveTextConvo() {
        textConvo?.toChat()?.let { replaceTextBlock(textConvoRange, Texts.serialize(it)) }
        cancelTextConvo()
    }

    fun cancelTextConvo() {
        textConvo = null
        textConvoRange = null
    }

    /** Writes a texts [block] over [range] (an existing block) or at the cursor, then moves the cursor past it. */
    fun replaceTextBlock(range: IntRange?, block: String) {
        val (next, cursor) = Texts.splice(body, range, selection.start.coerceIn(0, body.length), block)
        editBody(next)
        selection = TextRange(cursor)
        composition = null
    }

    /** The current body rendered as AO3-ready HTML, for pasting into the AO3 work form. */
    fun ao3Html(): String = Ao3Markdown.toHtml(body)

    // --- cast: written straight through (no debounce), like relationships.

    val cast: Flow<List<CastForChapter>> = repo.cast(id)

    /** Candidate cast members: live entity search over the world. */
    fun searchCast(text: String): Flow<List<Entity>> = entities.search(worldId, text)

    fun addCast(entityId: String) {
        scope.launch { repo.addAppearance(id, entityId) }
    }

    fun removeCast(entityId: String) {
        scope.launch { repo.removeAppearance(id, entityId) }
    }

    suspend fun flush() {
        saveJob?.cancelAndJoin()
        save()
    }

    private fun edit(change: () -> Unit) {
        change()
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch { delay(EntityEditorState.SAVE_DELAY_MS); save() }
    }

    private suspend fun save() {
        if (!dirty) return
        dirty = false
        try {
            withContext(NonCancellable) { repo.updateChapter(id, title, body) }
        } catch (e: Throwable) {
            dirty = true // still unsaved: the next edit or flush retries it
            throw e
        }
    }
}
