package dev.grimoire.world

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
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

    suspend fun create(name: String) = withContext(Dispatchers.IO) {
        val now = clock()
        db.grimoireQueries.insertWorld(
            id = ids.next(),
            name = name,
            summary = null,
            kind = "original",
            settings = "{}",
            created_at = now,
            updated_at = now,
        )
    }
}
