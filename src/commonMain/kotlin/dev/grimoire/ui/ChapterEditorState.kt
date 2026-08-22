package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.work.Ao3Markdown
import dev.grimoire.work.WorkRepository
import dev.grimoire.work.Words
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var title by mutableStateOf(""); private set
    var body by mutableStateOf(""); private set
    var dirty by mutableStateOf(false); private set

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
    fun editBody(v: String) = edit { body = v }

    /** The current body rendered as AO3-ready HTML, for pasting into the AO3 work form. */
    fun ao3Html(): String = Ao3Markdown.toHtml(body)

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
        withContext(NonCancellable) { repo.updateChapter(id, title, body) }
    }
}
