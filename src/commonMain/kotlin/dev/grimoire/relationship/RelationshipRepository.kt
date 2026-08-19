package dev.grimoire.relationship

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.db.RelationshipsFor
import dev.grimoire.id.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RelationshipRepository(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
) {
    private val q get() = db.grimoireQueries

    /** Every live edge touching [entityId], outgoing and incoming, with the label as seen from that side. */
    fun forEntity(entityId: String): Flow<List<RelationshipsFor>> =
        q.relationshipsFor(entityId, entityId).asFlow().mapToList(Dispatchers.IO)

    suspend fun create(
        worldId: String, fromEntity: String, toEntity: String,
        kind: String, label: String?, inverseLabel: String?,
    ): String = withContext(Dispatchers.IO) {
        require(fromEntity != toEntity) { "an entity cannot relate to itself" }
        val now = clock()
        val id = ids.next()
        q.insertRelationship(
            id = id, world_id = worldId, from_entity = fromEntity, to_entity = toEntity, kind = kind,
            label = label?.ifBlank { null }, inverse_label = inverseLabel?.ifBlank { null }, notes = null,
            created_at = now, updated_at = now,
        )
        id
    }

    suspend fun softDelete(id: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteRelationship(deleted_at = now, updated_at = now, id = id)
    }
}
