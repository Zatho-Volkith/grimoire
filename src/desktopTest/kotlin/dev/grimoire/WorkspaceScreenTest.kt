package dev.grimoire

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.export.ExportSink
import dev.grimoire.export.WorldExporter
import dev.grimoire.template.TemplateRepository
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.work.WorkRepository
import dev.grimoire.ui.App
import dev.grimoire.ui.Session
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.assertEquals

/** End-to-end through the real Compose tree and a real SQLite file. */
@OptIn(ExperimentalTestApi::class)
class WorkspaceScreenTest {
    @get:Rule val rule = createComposeRule()

    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-ui").resolve("g.db")))
    private val ids = IdGenerator(Random(3), System::currentTimeMillis)
    private val worlds = WorldRepository(db, ids, System::currentTimeMillis)
    private val entities = EntityRepository(db, ids, System::currentTimeMillis)
    private val relationships = RelationshipRepository(db, ids, System::currentTimeMillis)
    private val works = WorkRepository(db, ids, System::currentTimeMillis)
    private val timeline = TimelineRepository(db, ids, System::currentTimeMillis)
    private val templates = TemplateRepository(db, ids, System::currentTimeMillis)
    private val session = Session(
        worlds, entities, relationships, works, timeline, templates,
        WorldExporter(db), ExportSink { _, _ -> "/fake/vault" }, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Test
    fun `create world, add a location, rename it, find it by search`() {
        rule.setContent { App(session) }

        rule.onNode(hasSetTextAction() and hasText("New world")).performTextInput("Arda")
        rule.onNodeWithText("Create").performClick()
        rule.waitUntil(5_000) { session.workspace != null }
        rule.waitUntilAtLeastOneExists(hasText("Arda"), 5_000)

        rule.onNodeWithText("Locations").performClick()
        rule.onNodeWithText("New Location").performClick()
        rule.waitUntil(5_000) { session.workspace?.editor?.loaded == true }
        rule.waitUntilAtLeastOneExists(hasText("New Location") and hasSetTextAction(), 5_000)

        val nameField = rule.onNode(hasSetTextAction() and hasText("Name"))
        nameField.performTextClearance()
        nameField.performTextInput("Rivertown")
        runBlocking { session.flush() }

        val ws = session.workspace!!
        val names = runBlocking { entities.byType(ws.worldId, EntityType.LOCATION).first() }.map { it.name }
        assertEquals(listOf("Rivertown"), names)

        rule.onNode(hasSetTextAction() and hasText("Search all entities")).performTextInput("river")
        rule.waitUntilAtLeastOneExists(hasText("1 result(s)"), 5_000)
        rule.onNodeWithText("Location").assertIsDisplayed() // type shown as subtitle in search results
    }

    @Test
    fun `relationship added through the panel appears on both entities`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        val rin = runBlocking { entities.create(w, EntityType.CHARACTER, "Rin") }
        session.openWorld(w)
        rule.setContent { App(session) }
        session.workspace!!.open(kaede)
        rule.waitUntilAtLeastOneExists(hasText("None yet."), 5_000)

        // Default kind is `knows`; pick Rin from the target search, add.
        rule.onNode(hasSetTextAction() and hasText("Find target entity")).performTextInput("ri")
        rule.waitUntilAtLeastOneExists(hasText("Rin") and hasText("Character") and hasClickAction(), 5_000)
        rule.onNode(hasText("Rin") and hasText("Character") and hasClickAction()).performClick()
        rule.onNodeWithText("Add").performClick()
        rule.waitUntilAtLeastOneExists(hasText("knows"), 5_000)

        val fromRin = runBlocking { relationships.forEntity(rin).first() }.single()
        assertEquals("known by", fromRin.shown_label)
        assertEquals("Kaede", fromRin.other_name)

        // The other end is a link: clicking it opens that entity.
        rule.onNode(hasTestTag("relationship-link") and hasText("Rin")).performClick()
        rule.waitUntil(5_000) { session.workspace?.editor?.id == rin }
        rule.waitUntilAtLeastOneExists(hasText("known by"), 5_000)
    }

    @Test
    fun `create a work, write a chapter, add a second one and switch back`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Works").performClick()
        rule.onNodeWithText("New Work").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.loaded == true }

        // The work opens straight into its auto-created first chapter.
        rule.onNode(hasSetTextAction() and hasText("Body")).performTextInput("The road goes *ever* on.")
        runBlocking { session.flush() }

        val workEd = session.workspace!!.workEditor!!
        val chapters = runBlocking { works.chapters(workEd.id).first() }
        assertEquals(1, chapters.size)
        assertEquals(5L, chapters.single().word_count)
        assertEquals("The road goes *ever* on.", chapters.single().body)

        rule.onNodeWithText("New chapter").performClick()
        rule.waitUntil(5_000) { runBlocking { works.chapters(workEd.id).first() }.size == 2 }
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.id == runBlocking { works.chapters(workEd.id).first() }[1].id }

        rule.waitUntilAtLeastOneExists(hasText("Chapter 1"), 5_000)
        rule.onNodeWithText("Chapter 1").performClick()
        rule.waitUntil(5_000) {
            session.workspace?.workEditor?.chapterEditor?.body == "The road goes *ever* on."
        }
    }

    @Test
    fun `create an event and link a participant who opens as an entity`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Timeline").performClick()
        rule.onNodeWithText("New Event").performClick()
        rule.waitUntil(5_000) { session.workspace?.eventEditor?.loaded == true }

        rule.onNode(hasSetTextAction() and hasText("When (free text)")).performTextInput("Third Age, 3019")
        runBlocking { session.flush() }
        val eventId = session.workspace!!.eventEditor!!.id
        assertEquals("Third Age, 3019", runBlocking { timeline.event(eventId).first() }!!.display_date)

        rule.onNode(hasSetTextAction() and hasText("Find entity")).performTextInput("kae")
        rule.waitUntilAtLeastOneExists(hasText("Kaede") and hasText("Character") and hasClickAction(), 5_000)
        rule.onNode(hasText("Kaede") and hasText("Character") and hasClickAction()).performClick()
        rule.onNodeWithText("Add").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("participant-link"), 5_000)

        // Clicking the participant jumps to that entity in the Entities section.
        rule.onNode(hasTestTag("participant-link")).performClick()
        rule.waitUntil(5_000) { session.workspace?.editor?.id == kaede }
    }

    @Test
    fun `a template's fields appear on matching entities and values persist`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Templates").performClick()
        rule.onNodeWithText("New Template").performClick()
        rule.waitUntil(5_000) { session.workspace?.templateEditor?.loaded == true }
        rule.onNode(hasSetTextAction() and hasText("New field")).performTextInput("Age")
        rule.onNodeWithText("Add field").performClick()
        runBlocking { session.flush() }
        val templateId = session.workspace!!.templateEditor!!.id

        session.workspace!!.open(kaede)
        rule.waitUntil(5_000) { session.workspace?.editor?.loaded == true }
        rule.onNodeWithText("None").performClick() // template dropdown
        rule.waitUntilAtLeastOneExists(hasText("New Character template") and hasClickAction(), 5_000)
        rule.onNode(hasText("New Character template") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("Age"), 5_000)
        rule.onNode(hasSetTextAction() and hasText("Age")).performTextInput("27")
        runBlocking { session.flush() }

        val row = runBlocking { entities.entity(kaede).first() }!!
        assertEquals(templateId, row.template_id)
        assertEquals("""{"age":"27"}""", row.attributes)
    }

    @Test
    fun `export button reports the destination from the sink`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Export").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Exported 1 file(s) to /fake/vault"), 5_000)
    }

    @Test
    fun `chapter preview renders markdown live while typing`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Works").performClick()
        rule.onNodeWithText("New Work").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.loaded == true }

        rule.onNode(hasSetTextAction() and hasText("Body")).performTextInput("Hello *world*\n\n---\n\nThe end.")

        // The preview shows the rendered text (no asterisks) and a real divider for ---.
        rule.waitUntilAtLeastOneExists(hasText("Hello world"), 5_000)
        rule.waitUntilAtLeastOneExists(hasText("The end."), 5_000)
        rule.onNodeWithTag("md-hr", useUnmergedTree = true).assertExists()

        // Hiding the preview removes it.
        rule.onNodeWithText("Hide preview").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.showPreview == false }
    }
}
