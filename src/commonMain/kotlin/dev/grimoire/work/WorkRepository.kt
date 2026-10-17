package dev.grimoire.work

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.grimoire.db.AppearancesForEntity
import dev.grimoire.db.CastForChapter
import dev.grimoire.db.Chapter
import dev.grimoire.db.SearchChapters
import dev.grimoire.db.DeletedChaptersForWorld
import dev.grimoire.db.DeletedWorksForWorld
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.db.Work
import dev.grimoire.entity.ftsQuery
import dev.grimoire.id.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Works and their chapters — one aggregate. Chapter bodies are Markdown (see [Ao3Markdown]). */
class WorkRepository(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
) {
    private val q get() = db.grimoireQueries

    fun forWorld(worldId: String): Flow<List<Work>> =
        q.worksForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    fun work(id: String): Flow<Work?> =
        q.workById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun chapters(workId: String): Flow<List<Chapter>> =
        q.chaptersForWork(workId).asFlow().mapToList(Dispatchers.IO)

    fun chapter(id: String): Flow<Chapter?> =
        q.chapterById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    /** Creates the work with an empty first chapter, so the editor opens straight into writing. */
    suspend fun create(worldId: String, title: String): String = withContext(Dispatchers.IO) {
        val now = clock()
        val workId = ids.next()
        db.transaction {
            q.insertWork(
                id = workId, world_id = worldId, title = title, summary = null,
                kind = "original", status = WorkStatus.DRAFT.key, created_at = now, updated_at = now,
            )
            q.insertChapter(
                id = ids.next(), work_id = workId, title = null, body = "",
                sort_position = 1, word_count = 0, created_at = now, updated_at = now,
            )
        }
        workId
    }

    suspend fun updateWork(id: String, title: String, summary: String?, status: WorkStatus) =
        withContext(Dispatchers.IO) {
            q.updateWorkContent(title = title, summary = summary, status = status.key, updated_at = clock(), id = id)
        }

    suspend fun softDeleteWork(id: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteWork(deleted_at = now, updated_at = now, id = id)
    }

    /** Appends a chapter after the highest position ever used in the work. Returns its id. */
    suspend fun addChapter(workId: String): String = withContext(Dispatchers.IO) {
        val now = clock()
        val id = ids.next()
        db.transaction {
            q.insertChapter(
                id = id, work_id = workId, title = null, body = "",
                sort_position = q.nextChapterPosition(workId).executeAsOne(), word_count = 0,
                created_at = now, updated_at = now,
            )
        }
        id
    }

    suspend fun updateChapter(id: String, title: String?, body: String) = withContext(Dispatchers.IO) {
        q.updateChapterContent(
            title = title?.ifBlank { null }, body = body, word_count = Words.count(body),
            updated_at = clock(), id = id,
        )
    }

    /** Swaps the chapter with its live neighbor above ([delta] = -1) or below (+1). No-op at the edges. */
    suspend fun moveChapter(id: String, delta: Int) = withContext(Dispatchers.IO) {
        require(delta == 1 || delta == -1) { "delta must be ±1" }
        val now = clock()
        db.transaction {
            val chapter = q.chapterById(id).executeAsOneOrNull() ?: return@transaction
            val siblings = q.chaptersForWork(chapter.work_id).executeAsList()
            val index = siblings.indexOfFirst { it.id == id }
            val neighbor = siblings.getOrNull(index + delta) ?: return@transaction
            q.setChapterPosition(sort_position = neighbor.sort_position, updated_at = now, id = chapter.id)
            q.setChapterPosition(sort_position = chapter.sort_position, updated_at = now, id = neighbor.id)
        }
    }

    suspend fun softDeleteChapter(id: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteChapter(deleted_at = now, updated_at = now, id = id)
    }

    /** Live FTS over chapter title/body across the whole world. Blank [text] yields no results. */
    fun searchChapters(worldId: String, text: String): Flow<List<SearchChapters>> {
        val match = ftsQuery(text) ?: return flowOf(emptyList())
        return q.searchChapters(match, worldId).asFlow().mapToList(Dispatchers.IO)
    }

    // --- appearances ---

    /** The chapter's cast, alphabetical. */
    fun cast(chapterId: String): Flow<List<CastForChapter>> =
        q.castForChapter(chapterId).asFlow().mapToList(Dispatchers.IO)

    /** Every chapter (with its work) that casts [entityId], in reading order per work. */
    fun appearances(entityId: String): Flow<List<AppearancesForEntity>> =
        q.appearancesForEntity(entityId).asFlow().mapToList(Dispatchers.IO)

    /** Adds (or revives) [entityId] in the chapter's cast. */
    suspend fun addAppearance(chapterId: String, entityId: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.upsertAppearance(entity_id = entityId, chapter_id = chapterId, note = null, created_at = now, updated_at = now)
    }

    suspend fun removeAppearance(chapterId: String, entityId: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteAppearance(deleted_at = now, updated_at = now, entity_id = entityId, chapter_id = chapterId)
    }

    // --- trash ---

    fun deletedWorks(worldId: String): Flow<List<DeletedWorksForWorld>> =
        q.deletedWorksForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    fun deletedChapters(worldId: String): Flow<List<DeletedChaptersForWorld>> =
        q.deletedChaptersForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    suspend fun restoreWork(id: String) = withContext(Dispatchers.IO) {
        q.restoreWork(updated_at = clock(), id = id)
    }

    /** The chapter returns at its original sort_position (positions are never reused), with its work if that was trashed too. */
    suspend fun restoreChapter(id: String) = withContext(Dispatchers.IO) {
        val now = clock()
        db.transaction {
            q.restoreChapter(updated_at = now, id = id)
            q.restoreWorkOfChapter(updated_at = now, id = id)
        }
    }
}
