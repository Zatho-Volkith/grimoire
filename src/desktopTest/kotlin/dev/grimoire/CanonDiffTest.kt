package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.json.JsonStringMap
import dev.grimoire.net.Fetcher
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.TemplateRepository
import dev.grimoire.ui.EntityEditorState
import dev.grimoire.variant.VariantRepository
import dev.grimoire.wiki.WikiConnector
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonDiffTest {
    private val fixture: String =
        javaClass.classLoader.getResourceAsStream("teleportation.json")!!.readBytes().decodeToString()
    private val ids = IdGenerator(Random(59)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val relationships = RelationshipRepository(db, ids) { 1L }
    private val templates = TemplateRepository(db, ids) { 1L }
    private val works = WorkRepository(db, ids) { 1L }
    private val variants = VariantRepository(db, ids) { 1L }
    private val wiki = WikiConnector(db, ids, { 1L }, Fetcher { fixture })

    @Test
    fun `attribute drift counts as drift, and take-canon restores fields one by one`() = runTest {
        val w = worlds.create("W")
        val id = wiki.import(w, "powerlisting.fandom.com", "Teleportation", EntityType.CONCEPT)

        val editor = EntityEditorState(id, entities, relationships, templates, works, variants, wiki, backgroundScope)
        editor.load()
        assertFalse(editor.differsFromCanon, "fresh import matches canon")

        editor.editName("Blinking")
        editor.editAttribute("Power/Ability to", "My own phrasing")
        assertTrue(editor.differsFromCanon)

        editor.takeCanonName()
        assertEquals("Teleportation", editor.name)
        assertTrue(editor.differsFromCanon, "attribute still differs")

        editor.takeCanonAttribute("Power/Ability to")
        assertFalse(editor.differsFromCanon)

        editor.flush()
        val e = entities.entity(id).first()!!
        assertEquals("Teleportation", e.name)
        assertTrue(JsonStringMap.decode(e.attributes).getValue("Power/Ability to").startsWith("Instantly transport"))
    }
}
