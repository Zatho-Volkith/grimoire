package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.relationship.RelationshipKind
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.TemplateRepository
import dev.grimoire.ui.EntityEditorState
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RelationshipRepositoryTest {
    private val ids = IdGenerator(Random(11)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val relationships = RelationshipRepository(db, ids) { 1L }
    private val templates = TemplateRepository(db, ids) { 1L }

    @Test
    fun `an edge is stored once and each end sees its own label`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        val guild = entities.create(w, EntityType.FACTION, "The Guild")
        val id = relationships.create(w, kaede, guild, RelationshipKind.MEMBER_OF.key, "member of", "has member")

        val fromKaede = relationships.forEntity(kaede).first().single()
        assertEquals("outgoing", fromKaede.direction)
        assertEquals("member of", fromKaede.shown_label)
        assertEquals("The Guild" to "faction", fromKaede.other_name to fromKaede.other_type)

        val fromGuild = relationships.forEntity(guild).first().single()
        assertEquals("incoming", fromGuild.direction)
        assertEquals("has member", fromGuild.shown_label)
        assertEquals("Kaede", fromGuild.other_name)

        relationships.softDelete(id)
        assertTrue(relationships.forEntity(kaede).first().isEmpty())
        assertTrue(relationships.forEntity(guild).first().isEmpty())
    }

    @Test
    fun `self-relationships are rejected and soft-deleted targets drop out`() = runTest {
        val w = worlds.create("Arda")
        val a = entities.create(w, EntityType.CHARACTER, "A")
        val b = entities.create(w, EntityType.CHARACTER, "B")
        assertFailsWith<IllegalArgumentException> { relationships.create(w, a, a, "knows", null, null) }

        relationships.create(w, a, b, "knows", null, null)
        assertEquals("knows", relationships.forEntity(a).first().single().let { it.shown_label ?: it.kind })
        entities.softDelete(b)
        assertTrue(relationships.forEntity(a).first().isEmpty(), "edges to deleted entities are hidden")
    }

    @Test
    fun `editor target search excludes the entity itself`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        entities.create(w, EntityType.LOCATION, "Kaede's Village")
        val editor = EntityEditorState(kaede, entities, relationships, templates, backgroundScope)
        editor.load()
        assertEquals(listOf("Kaede's Village"), editor.searchTargets("kae").first().map { it.name })
    }
}
