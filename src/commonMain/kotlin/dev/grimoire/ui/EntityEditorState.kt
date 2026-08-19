package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.entity.EntityRepository
import dev.grimoire.json.JsonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editing session for one entity. Edits land in local state immediately and are written back
 * after [SAVE_DELAY_MS] of quiet; [flush] writes synchronously-in-suspend for navigation and exit,
 * so a debounce can never lose the last keystrokes.
 */
class EntityEditorState(
    val id: String,
    private val repo: EntityRepository,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var name by mutableStateOf(""); private set
    var aliasesText by mutableStateOf(""); private set
    var summary by mutableStateOf(""); private set
    var body by mutableStateOf(""); private set
    var dirty by mutableStateOf(false); private set

    private var saveJob: Job? = null

    suspend fun load() {
        val e = repo.entity(id).first() ?: return
        name = e.name
        aliasesText = JsonStrings.decode(e.aliases).joinToString(", ")
        summary = e.summary.orEmpty()
        body = e.body.orEmpty()
        loaded = true
    }

    fun editName(v: String) = edit { name = v }
    fun editAliasesText(v: String) = edit { aliasesText = v }
    fun editSummary(v: String) = edit { summary = v }
    fun editBody(v: String) = edit { body = v }

    private fun edit(change: () -> Unit) {
        change()
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch { delay(SAVE_DELAY_MS); save() }
    }

    /** Persists pending edits now. Safe to call when nothing is dirty. */
    suspend fun flush() {
        // Cancels a save still waiting out its delay; waits for one already writing (the write
        // itself is NonCancellable, so cancelling mid-write can't drop the edit that cleared `dirty`).
        saveJob?.cancelAndJoin()
        save()
    }

    private suspend fun save() {
        if (!dirty) return
        dirty = false
        withContext(NonCancellable) {
            repo.updateContent(
                id = id,
                name = name.trim().ifEmpty { "Untitled" },
                aliases = aliasesText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                summary = summary.ifBlank { null },
                body = body.ifBlank { null },
            )
        }
    }

    companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
