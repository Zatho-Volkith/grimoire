package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.json.JsonStrings
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.ui.EntityEditorState
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityRepositoryTest {
    private var now = 1_700_000_000_000L
    private val ids = IdGenerator(Random(7)) { now }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { now }
    private val entities = EntityRepository(db, ids) { now }
    private val relationships = RelationshipRepository(db, ids) { now }

    @Test
    fun `world creation seeds a user_authored source and entities attach to it`() = runTest {
        val w = worlds.create("Arda")
        val source = db.grimoireQueries.defaultSourceForWorld(w).executeAsOne()
        assertEquals("user_authored", source.kind)

        val id = entities.create(w, EntityType.CHARACTER, "Kaede")
        val e = entities.entity(id).first()!!
        assertEquals(source.id, e.source_id)
        assertEquals("character", e.type)
        assertEquals(listOf("Kaede"), entities.byType(w, EntityType.CHARACTER).first().map { it.name })
        assertTrue(entities.byType(w, EntityType.LOCATION).first().isEmpty())
    }

    @Test
    fun `search is prefix, cross-type, world-scoped, and reactive to edits and deletes`() = runTest {
        val w = worlds.create("Arda")
        val other = worlds.create("Elsewhere")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        val village = entities.create(w, EntityType.LOCATION, "Kaede's Village")
        entities.create(other, EntityType.CHARACTER, "Kaede of Elsewhere")

        assertEquals(setOf(kaede, village), entities.search(w, "kae").first().map { it.id }.toSet())
        assertTrue(entities.search(w, "   ").first().isEmpty())

        entities.updateContent(kaede, "Kaede", aliases = listOf("The North Wind"), summary = "swordswoman", body = null)
        assertEquals(listOf(kaede), entities.search(w, "north wind").first().map { it.id })
        assertEquals(listOf("The North Wind"), JsonStrings.decode(entities.entity(kaede).first()!!.aliases))

        entities.softDelete(kaede)
        assertEquals(listOf(village), entities.search(w, "kae").first().map { it.id })
        assertEquals(listOf<String>(), entities.byType(w, EntityType.CHARACTER).first().map { it.id })
    }

    @Test
    fun `editor debounces writes and flush persists the latest keystrokes`() = runTest {
        val w = worlds.create("Arda")
        val id = entities.create(w, EntityType.CHARACTER, "Kaede")
        val editor = EntityEditorState(id, entities, relationships, backgroundScope)
        editor.load()
        assertTrue(editor.loaded)

        // Reads below are synchronous on purpose: suspending on Dispatchers.IO would let runTest
        // skip virtual time, firing the debounce early.
        val stored = { db.grimoireQueries.entityById(id).executeAsOne() }

        editor.editSummary("a swords")
        editor.editSummary("a swordswoman")
        assertTrue(editor.dirty)
        assertEquals(null, stored().summary, "nothing written before the quiet period")

        testScheduler.advanceTimeBy(EntityEditorState.SAVE_DELAY_MS - 1)
        assertTrue(editor.dirty, "still inside the quiet period")

        testScheduler.advanceTimeBy(2)
        testScheduler.runCurrent()
        assertFalse(editor.dirty, "the debounced save fired on its own")
        editor.flush() // only joins the in-flight write; dirty is already false so it writes nothing itself
        assertEquals("a swordswoman", stored().summary)

        editor.editName("Kaede Arisato")
        editor.editAliasesText("Kae, , The North Wind ")
        editor.flush()
        val saved = entities.entity(id).first()!!
        assertEquals("Kaede Arisato", saved.name)
        assertEquals(listOf("Kae", "The North Wind"), JsonStrings.decode(saved.aliases))
    }

    @Test
    fun `blank name saves as Untitled rather than empty`() = runTest {
        val w = worlds.create("Arda")
        val id = entities.create(w, EntityType.ITEM, "Sword")
        val editor = EntityEditorState(id, entities, relationships, backgroundScope)
        editor.load()
        editor.editName("   ")
        editor.flush()
        assertEquals("Untitled", entities.entity(id).first()!!.name)
    }
}
