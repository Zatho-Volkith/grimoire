package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.db.Chapter
import dev.grimoire.entity.EntityRepository
import dev.grimoire.work.WorkRepository
import dev.grimoire.work.WorkStatus
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
 * Editing session for one work: its own metadata (debounced like every content editor) plus
 * the chapter list and the currently open [ChapterEditorState].
 */
class WorkEditorState(
    val id: String,
    private val repo: WorkRepository,
    private val entities: EntityRepository,
    private val scope: CoroutineScope,
    private val initialChapterId: String? = null,
) {
    var loaded by mutableStateOf(false); private set
    var title by mutableStateOf(""); private set
    var summary by mutableStateOf(""); private set
    var status by mutableStateOf(WorkStatus.DRAFT); private set
    var dirty by mutableStateOf(false); private set
    var chapterEditor by mutableStateOf<ChapterEditorState?>(null); private set
    var worldId: String = ""; private set

    val chapters: Flow<List<Chapter>> = repo.chapters(id)

    private var saveJob: Job? = null
    private val pending = PendingWork(scope)

    suspend fun load() {
        val w = repo.work(id).first() ?: return
        worldId = w.world_id
        title = w.title
        summary = w.summary.orEmpty()
        status = WorkStatus.fromKey(w.status)
        loaded = true
        val target = initialChapterId ?: repo.chapters(id).first().firstOrNull()?.id
        target?.let { openChapter(it) }
    }

    fun editTitle(v: String) = edit { title = v }
    fun editSummary(v: String) = edit { summary = v }
    fun selectStatus(v: WorkStatus) = edit { status = v }

    // --- chapters ---

    fun openChapter(chapterId: String) {
        if (chapterEditor?.id == chapterId) return
        val previous = chapterEditor
        val next = ChapterEditorState(chapterId, repo, entities, worldId, scope)
        chapterEditor = next
        pending.launch {
            previous?.flush()
            next.load()
        }
    }

    fun createChapter() {
        scope.launch { openChapter(repo.addChapter(id)) }
    }

    fun deleteOpenChapter() {
        val target = chapterEditor ?: return
        chapterEditor = null
        scope.launch { repo.softDeleteChapter(target.id) }
    }

    /** Moves the open chapter one slot up (-1) or down (+1) in reading order. */
    fun moveOpenChapter(delta: Int) {
        val target = chapterEditor ?: return
        scope.launch { repo.moveChapter(target.id, delta) }
    }

    suspend fun flush() {
        pending.join()
        chapterEditor?.flush()
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
            withContext(NonCancellable) {
                repo.updateWork(id, title.trim().ifEmpty { "Untitled" }, summary.ifBlank { null }, status)
            }
        } catch (e: Throwable) {
            dirty = true // still unsaved: the next edit or flush retries it
            throw e
        }
    }
}
