package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.export.VaultNames
import dev.grimoire.export.WorldExporter
import dev.grimoire.id.IdGenerator
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.TemplateField
import dev.grimoire.template.TemplateRepository
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldExporterTest {
    private val ids = IdGenerator(Random(23)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val relationships = RelationshipRepository(db, ids) { 1L }
    private val works = WorkRepository(db, ids) { 1L }
    private val timeline = TimelineRepository(db, ids) { 1L }
    private val templates = TemplateRepository(db, ids) { 1L }
    private val exporter = WorldExporter(db)

    private suspend fun List<dev.grimoire.export.ExportFile>.content(vararg path: String): String =
        single { it.path == path.toList() }.content

    @Test
    fun `entities export with frontmatter, wikilinked relationships, and unique names`() = runTest {
        val w = worlds.create("Arda")
        val sheet = templates.create(w, EntityType.CHARACTER, "Sheet")
        templates.update(sheet, "Sheet", listOf(TemplateField("age", "Age")))

        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        entities.updateContent(
            kaede, "Kaede", aliases = listOf("The North Wind"), summary = "A swordswoman.",
            body = "Long *notes*.", templateId = sheet, attributes = mapOf("age" to "27"),
        )
        val impostor = entities.create(w, EntityType.CHARACTER, "Kaede") // duplicate name
        val guild = entities.create(w, EntityType.FACTION, "The Guild")
        relationships.create(w, kaede, guild, "member_of", "member of", "has member")

        val files = exporter.export(w)

        val kaedeFile = files.content("Characters", "Kaede.md")
        assertTrue("type: Character" in kaedeFile)
        assertTrue("aliases:" in kaedeFile && "  - \"The North Wind\"" in kaedeFile)
        assertTrue("Age: \"27\"" in kaedeFile)
        assertTrue("A swordswoman." in kaedeFile && "Long *notes*." in kaedeFile)
        assertTrue("- member of [[The Guild]]" in kaedeFile)

        assertTrue("- has member [[Kaede]]" in files.content("Factions", "The Guild.md"))
        files.content("Characters", "Kaede 2.md") // the duplicate got a distinct vault name
        assertEquals(2, entities.entity(impostor).first()!!.let { 2 }) // impostor still exists in db
    }

    @Test
    fun `works export chapter bodies verbatim under numbered file names`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "The Long Road")
        val c1 = works.chapters(work).first().single().id
        works.updateChapter(c1, "Departure", "She left at dawn.\n\n---\n\nAnd did not look back.")
        works.addChapter(work)

        val files = exporter.export(w)
        assertEquals(
            "She left at dawn.\n\n---\n\nAnd did not look back.\n",
            files.content("Works", "The Long Road", "01 Departure.md"),
        )
        assertEquals("", files.content("Works", "The Long Road", "02 Chapter 2.md"))
        val about = files.content("Works", "The Long Road", "About.md")
        assertTrue("# The Long Road" in about && "Status: Draft" in about)
    }

    @Test
    fun `timeline exports dates and participant wikilinks`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        val duel = timeline.create(w, "The duel")
        timeline.updateEvent(duel, "The duel", "Steel at dusk.", "Third Age, 3019", "Third Age")
        timeline.addParticipant(duel, kaede, "challenger")

        val text = exporter.export(w).content("Timeline.md")
        assertTrue("## The duel" in text)
        assertTrue("*Third Age, 3019 · Third Age*" in text)
        assertTrue("Steel at dusk." in text)
        assertTrue("Participants: [[Kaede]] (challenger)" in text)
    }

    @Test
    fun `file names are sanitized and empty worlds still export an About file`() = runTest {
        val w = worlds.create("Arda")
        entities.create(w, EntityType.LOCATION, "Bree/Chetwood: the *inn*?")

        val files = exporter.export(w)
        files.content("Locations", "Bree Chetwood the inn.md") // illegal chars collapsed
        assertTrue("# Arda" in files.content("About.md"))

        assertEquals("Untitled", VaultNames.sanitize("<:>"))
        assertEquals(listOf("About.md"), exporter.export(worlds.create("Empty")).map { it.path.single() })
    }

    @Test
    fun `each calendar exports its own timeline file`() = runTest {
        val w = worlds.create("Arda")
        timeline.create(w, "The Sundering")
        timeline.create(w, "Chapter one happens", "Story order")

        val files = exporter.export(w)
        assertTrue("## The Sundering" in files.content("Timeline.md"))
        val story = files.content("Timeline - Story order.md")
        assertTrue(story.startsWith("# Timeline — Story order"))
        assertTrue("## Chapter one happens" in story)
    }
}
