package dev.grimoire

import dev.grimoire.db.FtsMaintenance
import dev.grimoire.db.GrimoireDatabase
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import java.sql.DriverManager

/** Replays real migrations against the frozen v1 snapshot with data in it. */
class MigrationTest {

    @Test
    fun `migrating a populated v1 database to v2 backfills chapter search`() {
        val file = createTempDirectory("grimoire-migration").resolve("grimoire.db")
        Path("src/commonMain/sqldelight/databases/1.db").copyTo(file)

        // Write v1-era data with plain JDBC — no schema attached, no migration triggered.
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { c ->
            c.createStatement().use { st ->
                // Snapshots are pure schema dumps (user_version 0); a real v1 db is stamped 1.
                st.executeUpdate("PRAGMA user_version = 1")
                st.executeUpdate("INSERT INTO world(id, name, kind, settings, created_at, updated_at) VALUES ('w','Arda','original','{}',1,1)")
                st.executeUpdate("INSERT INTO work(id, world_id, title, kind, status, created_at, updated_at) VALUES ('wk','w','Fic','original','draft',1,1)")
                st.executeUpdate("INSERT INTO chapter(id, work_id, title, body, sort_position, word_count, created_at, updated_at) " +
                    "VALUES ('c1','wk','Departure','She left at dawn.',1,4,1,1)")
            }
        }

        // Opening through the factory migrates 1 -> 2 and fires AfterVersion(2).
        val driver = DriverFactory.create(file)
        val db = GrimoireDatabase(driver)

        val migrated = driver.executeQuery(null, "PRAGMA user_version", { cur -> cur.next(); app.cash.sqldelight.db.QueryResult.Value(cur.getLong(0)!!) }, 0).value
        assertEquals(GrimoireDatabase.Schema.version, migrated)

        // Pre-migration chapters are searchable: the AfterVersion rebuild backfilled the index.
        val hits = db.grimoireQueries.searchChapters("\"dawn\"*", "w").executeAsList()
        assertEquals(listOf("c1"), hits.map { it.id })
        assertEquals("Fic", hits.single().work_title)
        FtsMaintenance.integrityCheck(driver)

        // And the old data survived untouched, with v3 defaults applied.
        assertEquals("Arda", db.grimoireQueries.selectWorlds().executeAsList().single().name)
        driver.execute(null, "INSERT INTO timeline_event(id, world_id, calendar_key, title, sort_position, created_at, updated_at) VALUES ('e1','w','default','Old event',1,1,1)", 0)
        val event = db.grimoireQueries.eventById("e1").executeAsOne()
        assertEquals("right" to null, event.side to event.parent_id)
    }

    @Test
    fun `migrating v1 templates to v4 assigns builtin keys`() {
        val file = createTempDirectory("grimoire-migration").resolve("grimoire.db")
        Path("src/commonMain/sqldelight/databases/1.db").copyTo(file)
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { c ->
            c.createStatement().use { st ->
                st.executeUpdate("PRAGMA user_version = 1")
                st.executeUpdate("INSERT INTO world(id, name, kind, settings, created_at, updated_at) VALUES ('w','Arda','original','{}',1,1)")
                st.executeUpdate("INSERT INTO template(id, world_id, entity_type, name, fields, is_builtin, created_at, updated_at) VALUES ('t1',NULL,'character','People','[]',1,1,1)")
                st.executeUpdate("INSERT INTO template(id, world_id, entity_type, name, fields, is_builtin, created_at, updated_at, deleted_at) VALUES ('t2',NULL,'location','Places','[]',1,1,1,5)")
                st.executeUpdate("INSERT INTO template(id, world_id, entity_type, name, fields, is_builtin, created_at, updated_at) VALUES ('t3','w','character','Mine','[]',0,1,1)")
            }
        }
        val driver = DriverFactory.create(file)
        val db = GrimoireDatabase(driver)
        val q = db.grimoireQueries
        assertEquals("character", q.templateById("t1").executeAsOne().builtin_key)
        assertEquals("location", q.templateById("t2").executeAsOne().builtin_key)
        assertEquals(null, q.templateById("t3").executeAsOne().builtin_key)
        listOf("t1", "t2", "t3").forEach { assertEquals(null, q.templateById(it).executeAsOne().body_scaffold) }
        val v = driver.executeQuery(null, "PRAGMA user_version", { cur -> cur.next(); app.cash.sqldelight.db.QueryResult.Value(cur.getLong(0)!!) }, 0).value
        assertEquals(GrimoireDatabase.Schema.version, v)
        assertEquals(4L, v)
    }
}
