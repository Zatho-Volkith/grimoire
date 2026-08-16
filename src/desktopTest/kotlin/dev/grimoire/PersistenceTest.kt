package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.id.IdGenerator
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PersistenceTest {

    private fun tempDbFile() = createTempDirectory("grimoire-test").resolve("grimoire.db")
    private val ids = IdGenerator(Random(1)) { 1_700_000_000_000 }

    @Test
    fun `world survives closing and reopening the database file`() = runBlocking {
        val file = tempDbFile()

        val first = DriverFactory.create(file)
        WorldRepository(GrimoireDatabase(first), ids) { 1L }.create("Middle-earth")
        first.close()

        val second = DriverFactory.create(file)
        val names = GrimoireDatabase(second).grimoireQueries.selectWorlds().executeAsList().map { it.name }
        second.close()

        assertEquals(listOf("Middle-earth"), names)
    }

    @Test
    fun `foreign keys are enforced by the driver`() {
        val db = GrimoireDatabase(DriverFactory.create(tempDbFile()))
        assertFailsWith<Exception> {
            db.grimoireQueries.upsertEntity(
                id = ids.next(), world_id = "no-such-world", source_id = "no-such-source", template_id = null,
                type = "character", name = "Ghost", aliases = "[]", summary = null, body = null,
                image_path = null, color = null, attributes = "{}", canon_snapshot = null,
                created_at = 1, updated_at = 1,
            )
        }
    }

    @Test
    fun `fts5 module is available and searchEntities executes`() {
        val db = GrimoireDatabase(DriverFactory.create(tempDbFile()))
        // No triggers populate entity_fts yet, so this is only a "the FTS5 table exists and is queryable" check.
        assertTrue(db.grimoireQueries.searchEntities("anything", "w").executeAsList().isEmpty())
    }
}
