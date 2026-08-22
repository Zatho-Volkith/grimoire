package dev.grimoire.entity

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.grimoire.db.Entity
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

    fun entity(id: String): Flow<Entity?> =
        q.entityById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    /** Live FTS over name/aliases/summary/body. Empty or whitespace-only [text] yields no results. */
    fun search(worldId: String, text: String): Flow<List<Entity>> {
        val match = ftsQuery(text) ?: return flowOf(emptyList())
        return q.searchEntities(match, worldId).asFlow().mapToList(Dispatchers.IO)
    }

    /** Creates a blank entity of [type] under the world's user-authored source. Returns its id. */
    suspend fun create(worldId: String, type: EntityType, name: String): String = withContext(Dispatchers.IO) {
        val source = q.defaultSourceForWorld(worldId).executeAsOne()
        val now = clock()
        val id = ids.next()
        q.upsertEntity(
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
}
