package dev.grimoire

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import dev.grimoire.db.FtsMaintenance
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.id.IdGenerator
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PersistenceTest {

    private fun tempDbFile() = createTempDirectory("grimoire-test").resolve("grimoire.db")
    private val ids = IdGenerator(Random(1)) { 1_700_000_000_000 }

    /** Fresh DB with one world ("w") and one source ("s") so entities can be inserted. */
    private fun seeded(): Pair<SqlDriver, GrimoireDatabase> {
        val driver = DriverFactory.create(tempDbFile())
        val db = GrimoireDatabase(driver)
        db.grimoireQueries.insertWorld("w", "World", null, "original", "{}", 1, 1)
        driver.execute(null, "INSERT INTO source(id, world_id, kind, label, created_at, updated_at) VALUES ('s','w','user_authored','me',1,1)", 0)
        return driver to db
    }

    private fun GrimoireDatabase.entity(id: String, name: String, body: String?) = grimoireQueries.upsertEntity(
        id = id, world_id = "w", source_id = "s", template_id = null, type = "character",
        name = name, aliases = "[]", summary = null, body = body, image_path = null, color = null,
        attributes = "{}", canon_snapshot = null, created_at = 1, updated_at = 1,
    )

    private fun GrimoireDatabase.search(term: String) =
        grimoireQueries.searchEntities(term, "w").executeAsList().map { it.name }.toSet()

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
    fun `driver enables WAL and foreign keys`() {
        val driver = DriverFactory.create(tempDbFile())
        val pragma = { name: String ->
            driver.executeQuery(null, "PRAGMA $name", { c -> c.next(); QueryResult.Value(c.getString(0)) }, 0).value
        }
        assertEquals("wal", pragma("journal_mode"))
        assertEquals("1", pragma("foreign_keys"))

        val db = GrimoireDatabase(driver)
        assertFailsWith<Exception>("dangling world_id must be rejected") {
            db.grimoireQueries.upsertEntity(
                ids.next(), "no-such-world", "no-such-source", null, "character", "Ghost", "[]",
                null, null, null, null, "{}", null, 1, 1,
            )
        }
    }

    @Test
    fun `fts triggers index inserts, updates and deletes`() {
        val (driver, db) = seeded()
        db.entity("e1", "Kaede", "a swordswoman from the north")
        db.entity("e2", "Rin", "a scholar")
        assertEquals(setOf("Kaede"), db.search("swordswoman"))

        // upsert's ON CONFLICT path is an UPDATE → AFTER UPDATE trigger
        db.entity("e2", "Rin", "a scholar of swordswoman lore")
        assertEquals(setOf("Kaede", "Rin"), db.search("swordswoman"))

        // soft delete keeps the row indexed but the query filters it
        db.grimoireQueries.softDeleteEntity(2, 2, "e1")
        assertEquals(setOf("Rin"), db.search("swordswoman"))

        // hard delete → AFTER DELETE trigger; index stays consistent
        driver.execute(null, "DELETE FROM entity WHERE id = 'e2'", 0)
        assertEquals(emptySet(), db.search("swordswoman"))
        FtsMaintenance.integrityCheck(driver)
    }

    @Test
    fun `fts rebuild recovers a wiped index`() {
        val (driver, db) = seeded()
        db.entity("e1", "Kaede", "a swordswoman")
        driver.execute(null, "INSERT INTO entity_fts(entity_fts) VALUES ('delete-all')", 0)
        assertEquals(emptySet(), db.search("swordswoman"))

        FtsMaintenance.rebuild(driver)
        assertEquals(setOf("Kaede"), db.search("swordswoman"))
        FtsMaintenance.integrityCheck(driver)
    }

    @Test
    fun `variant scope is exactly one of work or continuity, and unique per live scope`() {
        val (driver, db) = seeded()
        db.entity("e1", "Kaede", null)
        driver.execute(null, "INSERT INTO work(id, world_id, title, created_at, updated_at) VALUES ('wk','w','Fic',1,1)", 0)
        val insertVariant = { id: String, work: String?, cont: String?, deleted: Long? ->
            driver.execute(null, "INSERT INTO variant(id, entity_id, work_id, continuity_key, created_at, updated_at, deleted_at) VALUES (?,?,?,?,1,1,?)", 5) {
                bindString(0, id); bindString(1, "e1"); bindString(2, work); bindString(3, cont); bindLong(4, deleted)
            }
        }
        assertFailsWith<Exception>("neither scope") { insertVariant("v0", null, null, null) }
        assertFailsWith<Exception>("both scopes") { insertVariant("v0", "wk", "au", null) }

        insertVariant("v1", "wk", null, null)
        assertFailsWith<Exception>("second live variant for same work") { insertVariant("v2", "wk", null, null) }
        insertVariant("v3", "wk", null, 5) // soft-deleted duplicate is allowed
        insertVariant("v4", null, "au", null)

        val row = db.grimoireQueries.entityWithVariant("wk", "e1").executeAsList()
        assertEquals(1, row.size)
    }
}
