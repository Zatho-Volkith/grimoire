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
}
