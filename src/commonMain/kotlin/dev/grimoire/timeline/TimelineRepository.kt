package dev.grimoire.timeline

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.grimoire.db.DeletedEventsForWorld
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.db.ParticipantNamesForWorld
import dev.grimoire.db.ParticipantsForEvent
import dev.grimoire.db.Timeline_event
import dev.grimoire.id.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Timeline events and their participants. `sort_position` is the canonical order — the user
 * arranges events by hand; `display_date` is whatever the world's fiction calls that moment.
 * Everything lives on the world's default calendar until multi-calendar UI exists.
 */
class TimelineRepository(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
) {
    private val q get() = db.grimoireQueries

    fun events(worldId: String, calendarKey: String = CALENDAR): Flow<List<Timeline_event>> =
        q.timelineForWorld(worldId, calendarKey).asFlow().mapToList(Dispatchers.IO)

    /** Every calendar this world's events use ("default" plus whatever the user named). */
    fun calendars(worldId: String): Flow<List<String>> =
        q.calendarsForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    fun event(id: String): Flow<Timeline_event?> =
        q.eventById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    /** Appends an event at the end of [calendarKey]'s timeline. Returns its id. */
    suspend fun create(worldId: String, title: String, calendarKey: String = CALENDAR): String =
        withContext(Dispatchers.IO) {
            val now = clock()
            val id = ids.next()
            db.transaction {
                q.insertEvent(
                    id = id, world_id = worldId, calendar_key = calendarKey.trim().ifEmpty { CALENDAR },
                    title = title, description = null, display_date = null,
                    sort_position = q.nextEventPosition(worldId, calendarKey.trim().ifEmpty { CALENDAR }).executeAsOne(),
                    era = null, created_at = now, updated_at = now,
                )
            }
            id
        }

    suspend fun updateEvent(id: String, title: String, description: String?, displayDate: String?, era: String?) =
        withContext(Dispatchers.IO) {
            q.updateEventContent(
                title = title, description = description, display_date = displayDate, era = era,
                updated_at = clock(), id = id,
            )
        }

    /** Swaps the event with its live neighbor above ([delta] = -1) or below (+1). No-op at the edges. */
    suspend fun move(id: String, delta: Int) = withContext(Dispatchers.IO) {
        require(delta == 1 || delta == -1) { "delta must be ±1" }
        val now = clock()
        db.transaction {
            val event = q.eventById(id).executeAsOneOrNull() ?: return@transaction
            val siblings = q.timelineForWorld(event.world_id, event.calendar_key).executeAsList()
            val index = siblings.indexOfFirst { it.id == id }
            val neighbor = siblings.getOrNull(index + delta) ?: return@transaction
            q.setEventPosition(sort_position = neighbor.sort_position, updated_at = now, id = event.id)
            q.setEventPosition(sort_position = event.sort_position, updated_at = now, id = neighbor.id)
        }
    }

    suspend fun softDelete(id: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteEvent(deleted_at = now, updated_at = now, id = id)
    }

    fun deleted(worldId: String): Flow<List<DeletedEventsForWorld>> =
        q.deletedEventsForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        q.restoreEvent(updated_at = clock(), id = id)
    }

    // --- participants ---

    /** All participants across the world's events, for the viewer. */
    fun participantNames(worldId: String): Flow<List<ParticipantNamesForWorld>> =
        q.participantNamesForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    fun participants(eventId: String): Flow<List<ParticipantsForEvent>> =
        q.participantsForEvent(eventId).asFlow().mapToList(Dispatchers.IO)

    /** Adds (or revives) [entityId] as a participant. [role] is free text like "instigator". */
    suspend fun addParticipant(eventId: String, entityId: String, role: String?) = withContext(Dispatchers.IO) {
        val now = clock()
        q.upsertParticipant(
            event_id = eventId, entity_id = entityId, role = role?.ifBlank { null },
            created_at = now, updated_at = now,
        )
    }

    suspend fun removeParticipant(eventId: String, entityId: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteParticipant(deleted_at = now, updated_at = now, event_id = eventId, entity_id = entityId)
    }

    companion object {
        /** The calendar every world starts on. */
        const val CALENDAR = "default"
    }
}
