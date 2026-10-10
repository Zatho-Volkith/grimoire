package dev.grimoire

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import dev.grimoire.variant.VariantRepository
import dev.grimoire.work.WorkRepository
import dev.grimoire.ui.App
import dev.grimoire.ui.Session
import dev.grimoire.ui.WorkspaceSection
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
    private val settings = object : dev.grimoire.settings.SettingsStore {
        var saved = mapOf<String, String>()
        override fun load() = saved
        override fun save(values: Map<String, String>) { saved = values }
    }
    private val session = Session(
        worlds, entities, relationships, works, timeline, templates,
        VariantRepository(db, ids, System::currentTimeMillis),
        dev.grimoire.wiki.WikiConnector(db, ids, System::currentTimeMillis, dev.grimoire.net.Fetcher { error("no network in tests") }),
        WorldExporter(db), ExportSink { _, _ -> "/fake/vault" }, settings, dev.grimoire.sync.SyncStatus(),
        dev.grimoire.update.UpdateChecker({ error("no network in tests") }, "0.0.0"), noopUpdateLauncher,
        CoroutineScope(SupervisorJob() + Dispatchers.Default))

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
        rule.waitUntilAtLeastOneExists(hasText("1 RESULT(S)"), 5_000)
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
    fun `changing a field type in the template editor`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Templates").performClick()
        rule.onNodeWithText("New Template").performClick()
        rule.waitUntil(5_000) { session.workspace?.templateEditor?.loaded == true }
        rule.onNode(hasSetTextAction() and hasText("New field")).performTextInput("Backstory")
        rule.onNodeWithText("Add field").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("field-type-backstory"), 5_000)

        rule.onNodeWithTag("field-type-backstory").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Long") and hasClickAction(), 5_000)
        rule.onNode(hasText("Long") and hasClickAction()).performClick()
        runBlocking { session.flush() }

        val id = session.workspace!!.templateEditor!!.id
        val stored = dev.grimoire.template.TemplateFields.decode(runBlocking { templates.template(id).first() }!!.fields)
        assertEquals(dev.grimoire.template.FieldType.LONG, stored.single().type)
    }

    @Test
    fun `typing several options into the options field stores them all`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Templates").performClick()
        rule.onNodeWithText("New Template").performClick()
        rule.waitUntil(5_000) { session.workspace?.templateEditor?.loaded == true }
        rule.onNode(hasSetTextAction() and hasText("New field")).performTextInput("Language")
        rule.onNodeWithText("Add field").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("field-options-language"), 5_000)

        rule.onNodeWithTag("field-options-language").performTextInput("a, b")
        runBlocking { session.flush() }

        val id = session.workspace!!.templateEditor!!.id
        val stored = dev.grimoire.template.TemplateFields.decode(runBlocking { templates.template(id).first() }!!.fields)
        assertEquals(listOf("a", "b"), stored.single().options)
    }

    @Test
    fun `picking a race through the field autofills religion`() {
        dev.grimoire.template.BuiltinTemplates.seed(db, ids, System::currentTimeMillis)
        val w = runBlocking { worlds.create("Arda") }
        val sun = runBlocking { entities.create(w, EntityType.CONCEPT, "Sunworshippers") }
        val elf = runBlocking { entities.create(w, EntityType.CONCEPT, "Elf") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        runBlocking {
            val religion = templates.byBuiltinKey("concept.religion")!!.id
            val race = templates.byBuiltinKey("concept.race")!!.id
            val sheet = templates.byBuiltinKey("character")!!.id
            entities.updateContent(sun, "Sunworshippers", emptyList(), null, null, religion, emptyMap())
            entities.updateContent(elf, "Elf", emptyList(), null, null, race, emptyMap())
            entities.updateContent(kaede, "Kaede", emptyList(), null, null, sheet, emptyMap())
            relationships.create(w, elf, sun, "follows", "follows", "followed by")
        }
        session.openWorld(w)
        rule.setContent { App(session) }
        session.workspace!!.open(kaede)
        rule.waitUntil(5_000) { session.workspace?.editor?.loaded == true }

        rule.waitUntilAtLeastOneExists(hasTestTag("field-group") and hasText("Ties"), 5_000)
        rule.onNodeWithTag("link-add-race").performScrollTo().performClick()
        rule.onNode(hasTestTag("link-search")).performTextInput("Elf")
        rule.waitUntilAtLeastOneExists(hasText("Elf") and hasClickAction() and !hasSetTextAction(), 5_000)
        rule.onNode(hasText("Elf") and hasClickAction() and !hasSetTextAction()).performClick()

        rule.waitUntilAtLeastOneExists(hasTestTag("link-chip") and hasText("Sunworshippers"), 5_000)
        rule.waitUntilAtLeastOneExists(hasTestTag("autofill-note") and hasText("from Elf"), 5_000)
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

    @Test
    fun `texts block previews as bubbles`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Works").performClick()
        rule.onNodeWithText("New Work").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.loaded == true }

        rule.onNode(hasSetTextAction() and hasText("Body"))
            .performTextInput("::: texts Alex\n@me Sam\nSam: you up?\nAlex: nope\n~ Read\n:::")

        rule.waitUntilAtLeastOneExists(hasText("you up?"), 5_000)
        rule.onAllNodesWithTag("texts-me", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("texts-them", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag("texts-receipt", useUnmergedTree = true).assertExists()
        // One-on-one: the contact's name is the header, not a label on every bubble.
        rule.onAllNodes(hasText("Sam:"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `text convo dialog inserts a conversation`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Works").performClick()
        rule.onNodeWithText("New Work").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.loaded == true }

        rule.onNodeWithText("Text convo").performClick()
        rule.onNodeWithTag("convo-title").performTextInput("Alex")
        rule.onNodeWithTag("convo-owner").performTextInput("Sam")
        rule.onNodeWithText("+ Message").performClick()
        rule.onNodeWithTag("convo-sender-0").assertTextContains("Sam")
        rule.onNodeWithTag("convo-text-0").performTextInput("you up?")
        rule.onNodeWithText("+ Message").performClick()
        rule.onNodeWithTag("convo-sender-1").assertTextContains("Alex")
        rule.onNodeWithTag("convo-text-1").performTextInput("nope")
        rule.onNodeWithText("Save").performClick()

        rule.waitUntil(5_000) {
            session.workspace?.workEditor?.chapterEditor?.body == "::: texts Alex\n@me Sam\nSam: you up?\nAlex: nope\n:::"
        }
    }

    @Test
    fun `enter starts the next message, shift+enter breaks the line`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }
        rule.onNodeWithText("Works").performClick()
        rule.onNodeWithText("New Work").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.loaded == true }

        rule.onNodeWithText("Text convo").performClick()
        rule.onNodeWithTag("convo-title").performTextInput("Alex")
        rule.onNodeWithTag("convo-owner").performTextInput("Sam")
        rule.onNodeWithText("+ Message").performClick()
        rule.onNodeWithTag("convo-text-0").performTextInput("one")
        rule.onNodeWithTag("convo-text-0").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        rule.onNodeWithTag("convo-text-0").performTextInput("two")
        rule.onNodeWithTag("convo-text-0").performKeyInput { pressKey(Key.Enter) }

        rule.onNodeWithTag("convo-sender-1").assertTextContains("Alex")
        val convo = session.workspace!!.workEditor!!.chapterEditor!!.textConvo!!
        assertEquals(listOf("one\ntwo", ""), convo.rows.map { it.text })
    }

    @Test
    fun `a stray click outside does not throw away a conversation in progress`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }
        rule.onNodeWithText("Works").performClick()
        rule.onNodeWithText("New Work").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.loaded == true }

        rule.onNodeWithText("Text convo").performClick()
        rule.onNodeWithText("+ Message").performClick()
        rule.onNodeWithTag("convo-text-0").performTextInput("half-written")
        rule.onNodeWithTag("convo-text-0").performKeyInput { pressKey(Key.Escape) }
        rule.onAllNodes(isRoot()).onLast().performTouchInput { click(Offset(2f, 2f)) } // the dialog layer's corner, outside its card
        rule.waitForIdle()

        val convo = session.workspace?.workEditor?.chapterEditor?.textConvo
        assertEquals(listOf("half-written"), convo?.rows?.map { it.text })
    }

    @Test
    fun `text convo reopens an existing block`() {
        val w = runBlocking { worlds.create("Arda") }
        val work = runBlocking { works.create(w, "Fic") }
        val chapter = runBlocking { works.chapters(work).first() }.single().id
        val body = "Before.\n\n::: texts Alex\n@me Sam\nSam: hi\n:::"
        runBlocking { works.updateChapter(chapter, null, body) }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Works").performClick()
        rule.onNodeWithText("Fic").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.loaded == true }

        rule.onNode(hasSetTextAction() and hasText("Body")).performTextInputSelection(TextRange(body.indexOf("hi")))
        rule.onNodeWithText("Text convo").performClick()
        rule.onNodeWithTag("convo-title").assertTextContains("Alex")
        rule.onNodeWithTag("convo-text-0").assertTextContains("hi")
        rule.onNodeWithText("Cancel").performClick()

        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.textConvo == null }
        assertEquals(body, session.workspace?.workEditor?.chapterEditor?.body)
    }

    @Test
    fun `sidebar groups sections and navigates`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithTag("sidebar").assertExists()
        listOf("WORLD", "WRITING", "MANAGE").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithTag("nav-timeline").performClick()
        rule.waitUntil(5_000) { session.workspace?.section == WorkspaceSection.TIMELINE }
        rule.onNodeWithTag("nav-characters").performClick()
        rule.waitUntil(5_000) { session.workspace?.section == WorkspaceSection.ENTITIES }
    }

    @Test
    fun `theme row in the sidebar switches the palette`() {
        val w = runBlocking { worlds.create("Arda") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithTag("nav-theme").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Forest") and hasClickAction(), 5_000)
        rule.onNode(hasText("Forest") and hasClickAction()).performClick()
        rule.waitUntil(5_000) { session.theme == dev.grimoire.ui.AppTheme.FOREST }
    }

    @Test
    fun `theme menu switches the palette from the world picker`() {
        rule.setContent { App(session) }
        assertEquals(dev.grimoire.ui.AppTheme.GRIMOIRE, session.theme)

        rule.onNodeWithText("Theme: Grimoire").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Parchment") and hasClickAction(), 5_000)
        rule.onNode(hasText("Parchment") and hasClickAction()).performClick()
        rule.waitUntil(5_000) { session.theme == dev.grimoire.ui.AppTheme.PARCHMENT }
        assertEquals("parchment", settings.saved["theme"])
    }

    @Test
    fun `deleting an entity sends it to trash and restore brings it back`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        session.openWorld(w)
        rule.setContent { App(session) }
        session.workspace!!.open(kaede)
        rule.waitUntil(5_000) { session.workspace?.editor?.loaded == true }

        rule.onNodeWithText("Delete").performClick()
        rule.waitUntil(5_000) { runBlocking { entities.byType(w, EntityType.CHARACTER).first() }.isEmpty() }

        rule.onNodeWithText("Trash").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Kaede"), 5_000)
        rule.onNodeWithText("Restore").performClick()
        rule.waitUntil(5_000) { runBlocking { entities.byType(w, EntityType.CHARACTER).first() }.size == 1 }
        rule.waitUntilAtLeastOneExists(hasText("Trash is empty."), 5_000)
    }

    @Test
    fun `cast links chapter and entity in both directions`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        val work = runBlocking { works.create(w, "Fic") }
        val chapter = runBlocking { works.chapters(work).first() }.single().id
        session.openWorld(w)
        rule.setContent { App(session) }

        session.workspace!!.openWork(work)
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.loaded == true }

        // Add Kaede to the cast via search.
        rule.onNode(hasSetTextAction() and hasText("Add to cast")).performTextInput("kae")
        rule.waitUntilAtLeastOneExists(hasText("Kaede") and hasClickAction(), 5_000)
        rule.onNode(hasText("Kaede") and hasClickAction()).performClick()
        rule.waitUntil(5_000) { runBlocking { works.cast(chapter).first() }.size == 1 }

        // Cast name jumps to the entity...
        rule.waitUntilAtLeastOneExists(hasTestTag("cast-link"), 5_000)
        rule.onNode(hasTestTag("cast-link")).performClick()
        rule.waitUntil(5_000) { session.workspace?.editor?.id == kaede }

        // ...and its "Appears in" row jumps back to that work and chapter.
        rule.waitUntilAtLeastOneExists(hasTestTag("appearance-link"), 5_000)
        rule.onNode(hasTestTag("appearance-link")).performScrollTo().performClick()
        rule.waitUntil(5_000) {
            session.workspace?.section == WorkspaceSection.WORKS &&
                session.workspace?.workEditor?.id == work &&
                session.workspace?.workEditor?.chapterEditor?.id == chapter
        }
    }

    @Test
    fun `chapter search in the works section opens the matching chapter`() {
        val w = runBlocking { worlds.create("Arda") }
        val work = runBlocking { works.create(w, "The Long Road") }
        val chapter = runBlocking { works.chapters(work).first() }.single().id
        runBlocking { works.updateChapter(chapter, "Departure", "She left at dawn.") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Works").performClick()
        rule.onNode(hasSetTextAction() and hasText("Search all chapters")).performTextInput("dawn")
        rule.waitUntilAtLeastOneExists(hasText("Departure"), 5_000)
        rule.onNodeWithText("Departure").performClick()
        rule.waitUntil(5_000) { session.workspace?.workEditor?.chapterEditor?.id == chapter }
    }

    @Test
    fun `a work variant is added and edited from the entity editor`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        val fic = runBlocking { works.create(w, "Fic") }
        session.openWorld(w)
        rule.setContent { App(session) }
        session.workspace!!.open(kaede)
        rule.waitUntil(5_000) { session.workspace?.editor?.loaded == true }

        rule.onNodeWithText("New variant for work…").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Fic") and hasClickAction(), 5_000)
        rule.onNode(hasText("Fic") and hasClickAction()).performClick()
        rule.waitUntil(5_000) { session.workspace?.editor?.variantEditor?.loaded == true }

        rule.onNode(hasSetTextAction() and hasText("Name override (blank = canon)")).performScrollTo()
            .performTextInput("Kaede of the North")
        runBlocking { session.flush() }

        val v = runBlocking { session.variants.forEntity(kaede).first() }.single()
        assertEquals("Kaede of the North", v.name_override)
        assertEquals(fic, v.work_id)
    }

    @Test
    fun `timeline viewer shows era, cards, and jumps to entities and the editor`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        val duel = runBlocking { timeline.create(w, "The duel") }
        runBlocking { timeline.updateEvent(duel, "The duel", "Steel at dusk.", "Year 340", "Third Age") }
        runBlocking { timeline.addParticipant(duel, kaede, "challenger") }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Timeline").performClick()
        rule.onNodeWithText("View timeline").performClick()
        rule.waitUntilAtLeastOneExists(hasText("The duel"), 5_000)
        rule.onNode(hasTextExactly("Third Age")).assertIsDisplayed() // era header, viewer only
        rule.onNode(hasTextExactly("Year 340")).assertIsDisplayed() // date gutter node

        // Participant chip jumps to the entity...
        rule.onNode(hasTestTag("viewer-participant"), useUnmergedTree = true).performClick()
        rule.waitUntil(5_000) { session.workspace?.editor?.id == kaede }

        // ...back in the viewer, the card drops into the editor.
        rule.onNodeWithText("Timeline").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("viewer-event"), 5_000)
        rule.onNode(hasTestTag("viewer-event")).performClick()
        rule.waitUntil(5_000) { session.workspace?.timelineView == false && session.workspace?.eventEditor?.id == duel }
    }

    @Test
    fun `viewer survives recurring eras and New Event flips back to editing`() {
        val w = runBlocking { worlds.create("Arda") }
        runBlocking {
            val a = timeline.create(w, "First war"); timeline.updateEvent(a, "First war", null, null, "Third Age")
            val b = timeline.create(w, "Interlude"); timeline.updateEvent(b, "Interlude", null, null, "Fourth Age")
            val c = timeline.create(w, "The return"); timeline.updateEvent(c, "The return", null, null, "Third Age")
        }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Timeline").performClick()
        rule.onNodeWithText("View timeline").performClick()
        // Recurring era = two "Third Age" headers; duplicate keys would crash the LazyColumn.
        rule.waitUntil(5_000) { rule.onAllNodes(hasTextExactly("Third Age")).fetchSemanticsNodes().size == 2 }

        rule.onNodeWithText("New Event").performClick()
        rule.waitUntil(5_000) { session.workspace?.timelineView == false && session.workspace?.eventEditor?.loaded == true }
    }

    @Test
    fun `viewer places cards per side and nests clickable sub-events`() {
        val w = runBlocking { worlds.create("Arda") }
        val big = runBlocking { timeline.create(w, "The war") }
        val sub = runBlocking { timeline.create(w, "The first skirmish") }
        runBlocking {
            timeline.setSide(big, "left")
            timeline.setParent(sub, big)
        }
        session.openWorld(w)
        rule.setContent { App(session) }

        rule.onNodeWithText("Timeline").performClick()
        rule.onNodeWithText("View timeline").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("viewer-subevent"), 5_000)
        rule.onNode(hasTestTag("viewer-subevent")).assertIsDisplayed()

        rule.onNode(hasTestTag("viewer-subevent")).performClick()
        rule.waitUntil(5_000) { session.workspace?.eventEditor?.id == sub && session.workspace?.timelineView == false }
    }
}
