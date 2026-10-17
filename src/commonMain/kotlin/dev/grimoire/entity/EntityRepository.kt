package dev.grimoire.entity

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.grimoire.db.DeletedEntitiesForWorld
import dev.grimoire.db.Entity
import dev.grimoire.db.Source
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.id.IdGenerator
import dev.grimoire.json.JsonStringMap
import dev.grimoire.json.JsonStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class EntityRepository(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
) {
    private val q get() = db.grimoireQueries

    fun byType(worldId: String, type: EntityType): Flow<List<Entity>> =
        q.entitiesByType(worldId, type.key).asFlow().mapToList(Dispatchers.IO)

    /** Every live entity of the world, one-shot. */
    suspend fun inWorld(worldId: String): List<Entity> = withContext(Dispatchers.IO) {
        q.entitiesForWorld(worldId).executeAsList()
    }

    fun entity(id: String): Flow<Entity?> =
        q.entityById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    suspend fun sourceById(id: String): Source? = withContext(Dispatchers.IO) {
        q.sourceById(id).executeAsOneOrNull()
    }

    // --- named user sources: provenance labels like "Fandom consensus" or "Headcanon bible".

    fun userSources(worldId: String): Flow<List<Source>> =
        q.userSourcesForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    suspend fun createSource(worldId: String, label: String): String = withContext(Dispatchers.IO) {
        val now = clock()
        val id = ids.next()
        q.insertSource(id = id, world_id = worldId, kind = "user_authored", label = label, created_at = now, updated_at = now)
        id
    }

    suspend fun renameSource(id: String, label: String) = withContext(Dispatchers.IO) {
        q.renameSource(label = label, updated_at = clock(), id = id)
    }

    /** Reassigns a user-authored entity to another user source. Straight-through, like relationships. */
    suspend fun setSource(entityId: String, sourceId: String) = withContext(Dispatchers.IO) {
        q.setEntitySource(source_id = sourceId, updated_at = clock(), id = entityId)
    }

    /**
     * Live FTS over name/aliases/summary/body. Empty or whitespace-only [text] yields no results.
     * [sourceId] narrows to one source, [wikiOnly] to wiki imports — both applied before the hit cap.
     */
    fun search(worldId: String, text: String, sourceId: String? = null, wikiOnly: Boolean = false): Flow<List<Entity>> {
        val match = ftsQuery(text) ?: return flowOf(emptyList())
        return q.searchEntities(match, worldId, sourceId, if (wikiOnly) 1L else 0L).asFlow().mapToList(Dispatchers.IO)
    }

    /** Creates a blank entity of [type] under the world's user-authored source. Returns its id. */
    suspend fun create(worldId: String, type: EntityType, name: String): String = withContext(Dispatchers.IO) {
        val source = q.defaultSourceForWorld(worldId).executeAsOne()
        val now = clock()
        val id = ids.next()
        q.insertEntity(
            id = id, world_id = worldId, source_id = source.id, template_id = null, type = type.key,
            name = name, aliases = "[]", summary = null, body = null, image_path = null, color = null,
            attributes = "{}", canon_snapshot = null, created_at = now, updated_at = now,
        )
        id
    }

    suspend fun updateContent(
        id: String, name: String, aliases: List<String>, summary: String?, body: String?,
        templateId: String?, attributes: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        q.updateEntityContent(
            name = name, aliases = JsonStrings.encode(aliases), summary = summary, body = body,
            template_id = templateId, attributes = JsonStringMap.encode(attributes),
            updated_at = clock(), id = id,
        )
    }

    suspend fun softDelete(id: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteEntity(deleted_at = now, updated_at = now, id = id)
    }

    fun deleted(worldId: String): Flow<List<DeletedEntitiesForWorld>> =
        q.deletedEntitiesForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        q.restoreEntity(updated_at = clock(), id = id)
    }
}
