package dev.grimoire.variant

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.db.VariantById
import dev.grimoire.db.VariantsForEntity
import dev.grimoire.id.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Per-work (or per-continuity) overrides of a canon entity: "in this fic, she never left the
 * village." Stored once per (entity, scope); the schema's partial unique indexes reject a second
 * live variant for the same scope, so [create] returns null instead of throwing on duplicates.
 */
class VariantRepository(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
) {
    private val q get() = db.grimoireQueries

    fun forEntity(entityId: String): Flow<List<VariantsForEntity>> =
        q.variantsForEntity(entityId).asFlow().mapToList(Dispatchers.IO)

    fun variant(id: String): Flow<VariantById?> =
        q.variantById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    /** Exactly one of [workId]/[continuityKey] must be set. Null if that scope already has a live variant. */
    suspend fun create(entityId: String, workId: String?, continuityKey: String?): String? =
        withContext(Dispatchers.IO) {
            require((workId == null) != (continuityKey == null)) { "scope is exactly one of work or continuity" }
            val now = clock()
            val id = ids.next()
            runCatching {
                q.insertVariant(
                    id = id, entity_id = entityId, work_id = workId,
                    continuity_key = continuityKey?.trim()?.ifEmpty { null },
                    created_at = now, updated_at = now,
                )
            }.map { id }.getOrNull()
        }

    suspend fun updateContent(id: String, nameOverride: String?, summaryOverride: String?, bodyOverride: String?) =
        withContext(Dispatchers.IO) {
            q.updateVariantContent(
                name_override = nameOverride, summary_override = summaryOverride,
                body_override = bodyOverride, updated_at = clock(), id = id,
            )
        }

    suspend fun softDelete(id: String) = withContext(Dispatchers.IO) {
        val now = clock()
        q.softDeleteVariant(deleted_at = now, updated_at = now, id = id)
    }
}
