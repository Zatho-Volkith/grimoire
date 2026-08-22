package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.db.Entity
import dev.grimoire.db.ParticipantsForEvent
import dev.grimoire.entity.EntityRepository
import dev.grimoire.timeline.TimelineRepository
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
 * Editing session for one timeline event. Text fields follow the debounce+flush contract;
 * participants are written straight through, like relationships.
 */
class EventEditorState(
    val id: String,
    private val repo: TimelineRepository,
    private val entities: EntityRepository,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var worldId: String = ""; private set
    var title by mutableStateOf(""); private set
    var displayDate by mutableStateOf(""); private set
    var era by mutableStateOf(""); private set
    var description by mutableStateOf(""); private set
    var dirty by mutableStateOf(false); private set

    val participants: Flow<List<ParticipantsForEvent>> = repo.participants(id)

    private var saveJob: Job? = null

    suspend fun load() {
        val e = repo.event(id).first() ?: return
        worldId = e.world_id
        title = e.title
        displayDate = e.display_date.orEmpty()
        era = e.era.orEmpty()
        description = e.description.orEmpty()
        loaded = true
    }

    fun editTitle(v: String) = edit { title = v }
    fun editDisplayDate(v: String) = edit { displayDate = v }
    fun editEra(v: String) = edit { era = v }
    fun editDescription(v: String) = edit { description = v }

    /** Candidate participants: live search over the world's entities. */
    fun searchParticipants(text: String): Flow<List<Entity>> = entities.search(worldId, text)

    fun addParticipant(entityId: String, role: String) {
        scope.launch { repo.addParticipant(id, entityId, role) }
    }

    fun removeParticipant(entityId: String) {
        scope.launch { repo.removeParticipant(id, entityId) }
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
        withContext(NonCancellable) {
            repo.updateEvent(
                id = id,
                title = title.trim().ifEmpty { "Untitled event" },
                description = description.ifBlank { null },
                displayDate = displayDate.ifBlank { null },
                era = era.ifBlank { null },
            )
        }
    }
}
