package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.json.JsonStringMap
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.TemplateField
import dev.grimoire.template.TemplateFields
import dev.grimoire.template.TemplateRepository
import dev.grimoire.ui.EntityEditorState
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateRepositoryTest {
    private val ids = IdGenerator(Random(17)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val relationships = RelationshipRepository(db, ids) { 1L }
    private val templates = TemplateRepository(db, ids) { 1L }

    @Test
    fun `fields round-trip through the JSON column and filter by type`() = runTest {
        val w = worlds.create("Arda")
        val charSheet = templates.create(w, EntityType.CHARACTER, "Character sheet")
        templates.create(w, EntityType.LOCATION, "Place sheet")

        templates.update(charSheet, "Character sheet", listOf(TemplateField("age", "Age"), TemplateField("wants", "Wants")))

        val forCharacters = templates.forType(w, EntityType.CHARACTER).first()
        assertEquals(listOf("Character sheet"), forCharacters.map { it.name })
        assertEquals(
            listOf(TemplateField("age", "Age"), TemplateField("wants", "Wants")),
            TemplateFields.decode(forCharacters.single().fields),
        )
        assertEquals(2, templates.forWorld(w).first().size)

        templates.softDelete(charSheet)
        assertTrue(templates.forType(w, EntityType.CHARACTER).first().isEmpty())
    }

    @Test
    fun `field keys are slugged and never collide`() {
        assertEquals("eye_color", TemplateFields.keyFor("Eye color!", emptySet()))
        assertEquals("eye_color_2", TemplateFields.keyFor("Eye Color", setOf("eye_color")))
        assertEquals("field", TemplateFields.keyFor("!!!", emptySet()))
        assertEquals(emptyList(), TemplateFields.decode("not json"))
    }

    @Test
    fun `entity editor saves template choice and attribute values`() = runTest {
        val w = worlds.create("Arda")
        val sheet = templates.create(w, EntityType.CHARACTER, "Sheet")
        templates.update(sheet, "Sheet", listOf(TemplateField("age", "Age")))
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")

        val editor = EntityEditorState(kaede, entities, relationships, templates, backgroundScope)
        editor.load()
        assertEquals(listOf("Sheet"), editor.templateOptions.first().map { it.name })

        editor.selectTemplate(sheet)
        editor.editAttribute("age", "27")
        editor.flush()

        val row = entities.entity(kaede).first()!!
        assertEquals(sheet, row.template_id)
        assertEquals(mapOf("age" to "27"), JsonStringMap.decode(row.attributes))

        // Reload sees the same state; switching template keeps values.
        val editor2 = EntityEditorState(kaede, entities, relationships, templates, backgroundScope)
        editor2.load()
        assertEquals("27", editor2.attributes["age"])
        editor2.selectTemplate(null)
        editor2.flush()
        assertEquals(mapOf("age" to "27"), JsonStringMap.decode(entities.entity(kaede).first()!!.attributes))
    }
}
