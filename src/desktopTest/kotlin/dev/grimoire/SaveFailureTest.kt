package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.net.Fetcher
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.TemplateRepository
import dev.grimoire.ui.EntityEditorState
import dev.grimoire.variant.VariantRepository
import dev.grimoire.wiki.WikiConnector
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A save that fails must leave the edit pending, so the next flush (or close) retries it. */
class SaveFailureTest {
    private val ids = IdGenerator(Random(7), System::currentTimeMillis)
    private val driver = DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db"))
    private val db = GrimoireDatabase(driver)
    private val clock = System::currentTimeMillis
    private val entities = EntityRepository(db, ids, clock)

    @Test
    fun `a failed save stays dirty and the next flush writes it`() = runBlocking {
        val w = WorldRepository(db, ids, clock).create("Arda")
        val id = entities.create(w, EntityType.CHARACTER, "Kaede")
        val editor = EntityEditorState(
            id, entities, RelationshipRepository(db, ids, clock), TemplateRepository(db, ids, clock),
            WorkRepository(db, ids, clock), VariantRepository(db, ids, clock),
            WikiConnector(db, ids, clock, Fetcher { error("no network in tests") }),
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        editor.load()

        // Not TEMP: the driver may hand the write to another connection.
        driver.execute(null, "CREATE TRIGGER fail_save BEFORE UPDATE ON entity BEGIN SELECT RAISE(ABORT, 'disk full'); END", 0)
        editor.editName("Kaede the Exile")
        assertFailsWith<Exception> { editor.flush() }
        assertTrue(editor.dirty, "the edit is still pending")

        driver.execute(null, "DROP TRIGGER fail_save", 0)
        editor.flush()
        assertEquals("Kaede the Exile", entities.entity(id).first()!!.name)
    }
}
