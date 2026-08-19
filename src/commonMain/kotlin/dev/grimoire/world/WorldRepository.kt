package dev.grimoire.world

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.db.World
import dev.grimoire.id.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WorldRepository(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
) {
    fun worlds(): Flow<List<World>> =
        db.grimoireQueries.selectWorlds().asFlow().mapToList(Dispatchers.IO)

    fun world(id: String): Flow<World?> =
        db.grimoireQueries.worldById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    /** Creates the world plus its `user_authored` source, so entities can be added immediately. */
    suspend fun create(name: String): String = withContext(Dispatchers.IO) {
        val now = clock()
        val worldId = ids.next()
        db.transaction {
            db.grimoireQueries.insertWorld(
                id = worldId, name = name, summary = null, kind = "original", settings = "{}",
                created_at = now, updated_at = now,
            )
            db.grimoireQueries.insertSource(
                id = ids.next(), world_id = worldId, kind = "user_authored", label = "My notes",
                created_at = now, updated_at = now,
            )
        }
        worldId
    }
}
