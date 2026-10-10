package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.world.WorldRepository
import dev.grimoire.world.isArchived
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldArchiveTest {
    private val ids = IdGenerator(Random(71)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }

    @Test
    fun `archiving flags the world and unarchiving clears it`() = runTest {
        val w = worlds.create("Arda")
        worlds.archive(w, true)
        assertTrue(worlds.world(w).first()!!.isArchived)
        assertEquals(listOf(w), worlds.worlds().first().map { it.id }, "archived worlds stay in the live list; the picker splits them")
        worlds.archive(w, false)
        assertFalse(worlds.world(w).first()!!.isArchived)
    }

    @Test
    fun `archiving keeps other settings keys`() = runTest {
        val w = worlds.create("Arda")
        db.grimoireQueries.updateWorldSettings("""{"calendar":"shire"}""", 1L, w)
        worlds.archive(w, true)
        val settings = worlds.world(w).first()!!.settings
        assertTrue(""""calendar":"shire"""" in settings, settings)
        assertTrue(worlds.world(w).first()!!.isArchived)
    }

    @Test
    fun `malformed settings read as not archived and are replaced on archive`() = runTest {
        val w = worlds.create("Arda")
        db.grimoireQueries.updateWorldSettings("not json", 1L, w)
        assertFalse(worlds.world(w).first()!!.isArchived)
        worlds.archive(w, true)
        assertTrue(worlds.world(w).first()!!.isArchived)
    }

    @Test
    fun `trashing hides a world and restore brings it back with its contents`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        worlds.softDelete(w)
        assertTrue(worlds.worlds().first().isEmpty())
        assertEquals(listOf(w), worlds.deleted().first().map { it.id })

        worlds.restore(w)
        assertEquals(listOf(w), worlds.worlds().first().map { it.id })
        assertTrue(worlds.deleted().first().isEmpty())
        assertEquals(listOf(kaede), entities.byType(w, EntityType.CHARACTER).first().map { it.id })
    }
}
