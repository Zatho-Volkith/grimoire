package dev.grimoire.template

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.grimoire.db.DeletedTemplatesForWorld
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.db.Template
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** User-defined field sets: a "Character" template declares which attributes a character gets. */
class TemplateRepository(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
) {
    private val q get() = db.grimoireQueries

    fun forType(worldId: String, type: EntityType): Flow<List<Template>> =
        q.templatesForType(type.key, worldId).asFlow().mapToList(Dispatchers.IO)

    fun forWorld(worldId: String): Flow<List<Template>> =
        q.templatesForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    fun template(id: String): Flow<Template?> =
        q.templateById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    /** Creates an empty world-scoped template for [type]. Returns its id. */
    suspend fun create(worldId: String, type: EntityType, name: String): String = withContext(Dispatchers.IO) {
        val now = clock()
        val id = ids.next()
        q.insertTemplate(
            id = id, world_id = worldId, entity_type = type.key, name = name,
            fields = "[]", is_builtin = false, created_at = now, updated_at = now,
        )
        id
    }

    suspend fun update(id: String, name: String, fields: List<TemplateField>) = withContext(Dispatchers.IO) {
        q.updateTemplateContent(name = name, fields = TemplateFields.encode(fields), updated_at = clock(), id = id)
    }

    suspend fun softDelete(id: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteTemplate(deleted_at = now, updated_at = now, id = id)
    }

    fun deleted(worldId: String): Flow<List<DeletedTemplatesForWorld>> =
        q.deletedTemplatesForWorld(worldId).asFlow().mapToList(Dispatchers.IO)

    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        q.restoreTemplate(updated_at = clock(), id = id)
    }
}
