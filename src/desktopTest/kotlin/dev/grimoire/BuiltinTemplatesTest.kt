package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.template.AutofillRule
import dev.grimoire.template.BuiltinTemplates
import dev.grimoire.template.FieldType
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkSpec
import dev.grimoire.template.TemplateFields
import dev.grimoire.template.TemplateRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuiltinTemplatesTest {
    private val ids = IdGenerator(Random(61)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val templates = TemplateRepository(db, ids) { 1L }
    private val q get() = db.grimoireQueries

    private val legacyNames = mapOf(
        "character" to "Character sheet", "location" to "Location sheet", "faction" to "Faction sheet",
        "item" to "Item sheet", "concept" to "Concept sheet", "system" to "System sheet",
    )

    private fun insertLegacy(type: String, updatedAt: Long = 1L): String {
        val id = ids.next()
        q.insertTemplate(
            id = id, world_id = null, entity_type = type, name = legacyNames.getValue(type),
            fields = """[{"key":"kind","label":"Kind"},{"key":"age","label":"Age"}]""", is_builtin = true,
            builtin_key = type, body_scaffold = null, created_at = 1L, updated_at = updatedAt,
        )
        return id
    }

    @Test
    fun `fresh install gets all 17`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")

        assertEquals(17, templates.forWorld(w).first().size)
        assertEquals(17, BuiltinTemplates.all.size)
        assertEquals(
            BuiltinTemplates.all.map { it.key }.toSet(),
            templates.forWorld(w).first().map { it.builtin_key }.toSet(),
        )
        assertEquals(
            listOf("City", "Location sheet", "Nation", "Region"),
            templates.forType(w, EntityType.LOCATION).first().map { it.name },
        )
    }

    @Test
    fun `character sheet matches the catalogue`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val row = q.templateByBuiltinKey("character").executeAsOne()
        val fields = TemplateFields.decode(row.fields)
        assertEquals(
            listOf("Age", "Pronouns", "Occupation", "Appearance", "Personality", "Wants", "Fears",
                "Race", "Faction", "Religion", "Deity", "Languages", "Homeland"),
            fields.map { it.label },
        )
        val byKey = fields.associateBy { it.key }
        assertEquals(LinkSpec("member_of", LinkDir.OUT, "concept", "concept.race"), byKey.getValue("race").link)
        assertEquals(LinkSpec("member_of", LinkDir.OUT, "faction"), byKey.getValue("faction").link)
        assertEquals(
            listOf(AutofillRule("race", "deity"), AutofillRule("religion", "deities")),
            byKey.getValue("deity").autofill,
        )
        assertEquals(listOf("she/her", "he/him", "they/them"), byKey.getValue("pronouns").options)
        assertEquals("## Backstory\n\n## Arc\n\n## Voice notes\n", row.body_scaffold)
    }

    @Test
    fun `pre-v2 install gets only the new ones`() = runTest {
        legacyNames.keys.forEach { insertLegacy(it) }
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")

        val all = templates.forWorld(w).first()
        assertEquals(17, all.size)
        val keys = all.map { it.builtin_key }
        assertEquals(keys.size, keys.toSet().size, "no duplicate builtin_key")
    }

    @Test
    fun `untouched built-in is upgraded in place`() = runTest {
        val id = insertLegacy("location")
        BuiltinTemplates.seed(db, ids) { 5L }

        val row = q.templateById(id).executeAsOne()
        val located = assertNotNull(TemplateFields.decode(row.fields).firstOrNull { it.key == "located_in" })
        assertEquals(FieldType.LINK, located.type)
        assertEquals(1L, row.updated_at)
        assertEquals("## History\n\n## Inhabitants\n", row.body_scaffold)
    }

    @Test
    fun `edited built-in is left alone`() = runTest {
        val id = insertLegacy("character", updatedAt = 2L)
        val before = q.templateById(id).executeAsOne().fields
        BuiltinTemplates.seed(db, ids) { 5L }
        assertEquals(before, q.templateById(id).executeAsOne().fields)
    }

    @Test
    fun `deleted built-in stays deleted`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")
        val region = templates.forType(w, EntityType.LOCATION).first().single { it.name == "Region" }
        templates.softDelete(region.id)

        BuiltinTemplates.seed(db, ids) { 1L }

        assertTrue(templates.forType(w, EntityType.LOCATION).first().none { it.name == "Region" })
        assertEquals(1, q.templateByBuiltinKey("location.region").executeAsList().size)
    }

    @Test
    fun `seeding twice is a no-op`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")
        val first = templates.forWorld(w).first().associate { it.id to it.fields }
        BuiltinTemplates.seed(db, ids) { 2L }
        val second = templates.forWorld(w).first().associate { it.id to it.fields }
        assertEquals(first, second)
    }

    @Test
    fun `a user template named Region doesn't block the built-in`() = runTest {
        val w = worlds.create("Arda")
        templates.create(w, EntityType.LOCATION, "Region")
        BuiltinTemplates.seed(db, ids) { 1L }
        assertEquals(2, templates.forType(w, EntityType.LOCATION).first().count { it.name == "Region" })
    }

    @Test
    fun `original built-ins keep their frozen field keys`() {
        val frozen = mapOf(
            "character" to listOf("age", "pronouns", "occupation", "appearance", "personality", "wants", "fears"),
            "location" to listOf("kind", "located_in", "atmosphere", "notable_features"),
            "faction" to listOf("purpose", "leader", "headquarters", "allies", "rivals"),
            "item" to listOf("kind", "owner", "origin", "properties"),
            "concept" to listOf("domain", "rules_and_limits", "source_material"),
            "system" to listOf("how_it_works", "costs_and_limits", "practitioners"),
        )
        frozen.forEach { (key, keys) ->
            val def = BuiltinTemplates.all.single { it.key == key }
            val present = def.fields.map { it.key }.toSet()
            assertTrue(present.containsAll(keys), "$key missing ${keys - present}")
        }
    }
}
