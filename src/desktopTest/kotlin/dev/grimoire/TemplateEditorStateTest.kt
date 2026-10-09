package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.template.AutofillRule
import dev.grimoire.template.FieldType
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkSpec
import dev.grimoire.template.TemplateFields
import dev.grimoire.template.TemplateRepository
import dev.grimoire.ui.TemplateEditorState
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
import kotlin.test.assertNull

class TemplateEditorStateTest {
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-te").resolve("g.db")))
    private val ids = IdGenerator(Random(5), System::currentTimeMillis)
    private val worlds = WorldRepository(db, ids, System::currentTimeMillis)
    private val entities = EntityRepository(db, ids, System::currentTimeMillis)
    private val templates = TemplateRepository(db, ids, System::currentTimeMillis)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun editor(id: String) = TemplateEditorState(id, worldOf(id), templates, scope).also { runBlocking { it.load() } }

    private fun worldOf(id: String) = runBlocking { templates.template(id).first() }!!.world_id!!

    private fun newTemplate(): String = runBlocking {
        val w = worlds.create("Arda")
        templates.create(w, EntityType.CHARACTER, "Sheet")
    }

    private fun stored(id: String) = TemplateFields.decode(runBlocking { templates.template(id).first() }!!.fields)

    @Test
    fun `type, group, options, link and autofill edits persist`() {
        val id = newTemplate()
        val ed = editor(id)
        ed.addField("Home"); ed.addField("Language")
        ed.editFieldType("home", FieldType.LINK)
        assertEquals(LinkSpec("knows", LinkDir.OUT), ed.fields.first().link)
        ed.editFieldLink("home", LinkSpec("hails_from", LinkDir.OUT, "location", "location"))
        ed.editFieldGroup("language", "Culture")
        ed.editFieldOptions("language", " Elvish, ,Common ,")
        ed.editFieldAutofill("language", listOf(AutofillRule("home", "lang")))
        runBlocking { ed.flush() }

        val (home, lang) = stored(id)
        assertEquals(FieldType.LINK, home.type)
        assertEquals(LinkSpec("hails_from", LinkDir.OUT, "location", "location"), home.link)
        assertEquals("Culture", lang.group)
        assertEquals(listOf("Elvish", "Common"), lang.options)
        assertEquals(listOf(AutofillRule("home", "lang")), lang.autofill)
        assertEquals(listOf("Culture"), ed.groups)

        ed.editFieldGroup("language", "  ")
        runBlocking { ed.flush() }
        assertNull(stored(id)[1].group)
    }

    @Test
    fun `switching a link field back to text drops the link but keeps autofill`() {
        val id = newTemplate()
        val ed = editor(id)
        ed.addField("Home")
        ed.editFieldType("home", FieldType.LINK)
        ed.editFieldAutofill("home", listOf(AutofillRule("x", "y")))
        ed.editFieldType("home", FieldType.TEXT)
        runBlocking { ed.flush() }
        val f = stored(id).single()
        assertEquals(FieldType.TEXT, f.type)
        assertNull(f.link)
        assertEquals(listOf(AutofillRule("x", "y")), f.autofill)
    }

    @Test
    fun `body scaffold round-trips and blank saves null`() {
        val id = newTemplate()
        val ed = editor(id)
        ed.editBodyScaffold("## Appearance\n")
        runBlocking { ed.flush() }
        assertEquals("## Appearance\n", runBlocking { templates.template(id).first() }!!.body_scaffold)
        assertEquals("## Appearance\n", editor(id).bodyScaffold)

        ed.editBodyScaffold("   ")
        runBlocking { ed.flush() }
        assertNull(runBlocking { templates.template(id).first() }!!.body_scaffold)
    }

    @Test
    fun `legacy template survives an edit`() {
        val w = runBlocking { worlds.create("Arda") }
        val tid = ids.next()
        db.grimoireQueries.insertTemplate(
            tid, w, "character", "Legacy",
            """[{"key":"age","label":"Age"},{"key":"eye_colour","label":"Eyes"}]""",
            false, null, null, 1L, 1L,
        )
        val e = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        runBlocking { entities.updateContent(e, "Kaede", emptyList(), null, null, tid, mapOf("age" to "27", "eye_colour" to "grey")) }
        val before = runBlocking { entities.entity(e).first() }!!.attributes

        val ed = editor(tid)
        ed.editFieldGroup("age", "Basics")
        runBlocking { ed.flush() }

        assertEquals(listOf("age", "eye_colour"), stored(tid).map { it.key })
        assertEquals("Basics", stored(tid)[0].group)
        assertEquals(before, runBlocking { entities.entity(e).first() }!!.attributes)
    }

    @Test
    fun `a built-in template's editor lists the world's templates including itself and others`() {
        val w = runBlocking { worlds.create("Arda") }
        dev.grimoire.template.BuiltinTemplates.seed(db, ids, System::currentTimeMillis)
        val sheet = runBlocking { templates.byBuiltinKey("character")!! }
        val own = runBlocking { templates.create(w, EntityType.CHARACTER, "Mine") }
        val ed = TemplateEditorState(sheet.id, w, templates, scope).also { runBlocking { it.load() } }
        val keys = ed.worldTemplates.mapNotNull { it.builtin_key }
        assertEquals(true, "concept.race" in keys)
        assertEquals(true, "character" in keys)
        assertEquals(true, ed.worldTemplates.any { it.id == own })
    }

    @Test
    fun `group keeps typed spaces until saved`() {
        val id = newTemplate()
        val ed = editor(id)
        ed.addField("Home")
        ed.editFieldGroup("home", "Life ")
        assertEquals("Life ", ed.fields.single().group)
        runBlocking { ed.flush() }
        assertEquals("Life", stored(id).single().group)
    }
}
