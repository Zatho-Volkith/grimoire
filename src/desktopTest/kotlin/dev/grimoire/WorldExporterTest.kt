package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.export.VaultNames
import dev.grimoire.export.WorldExporter
import dev.grimoire.id.IdGenerator
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.BuiltinTemplates
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
        templates.update(sheet, "Sheet", listOf(TemplateField("age", "Age")), null)

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

    @Test
    fun `link fields export as frontmatter wikilinks`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")
        val city = templates.byBuiltinKey("location.city")!!.id
        val nation = templates.byBuiltinKey("location.nation")!!.id
        val eldoria = entities.create(w, EntityType.LOCATION, "Eldoria")
        entities.updateContent(eldoria, "Eldoria", emptyList(), null, null, nation, emptyMap())
        val silverport = entities.create(w, EntityType.LOCATION, "Silverport")
        entities.updateContent(silverport, "Silverport", emptyList(), null, null, city, mapOf("located_in" to "the old quarter"))
        relationships.create(w, silverport, eldoria, "located_in", "located in", "contains")

        val files = exporter.export(w)
        val text = files.content("Locations", "Silverport.md")
        assertTrue("Located in:\n  - \"[[Eldoria]]\"\n  - \"the old quarter\"\n" in text, text)
        assertTrue("- located in [[Eldoria]]" in text)
        assertEquals(1, Regex("Located in").findAll(text).count())
        assertTrue("Contains:" !in text)

        // links only, no text
        entities.updateContent(silverport, "Silverport", emptyList(), null, null, city, emptyMap())
        val linksOnly = exporter.export(w).content("Locations", "Silverport.md")
        assertTrue("Located in:\n  - \"[[Eldoria]]\"\n---" in linksOnly, linksOnly)

        // Eldoria's sheet has no out-field for this row, so it gets no "Located in" list
        assertTrue("Located in:" !in files.content("Locations", "Eldoria.md"))
    }

    @Test
    fun `link field wikilinks use sanitized vault names`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")
        val city = templates.byBuiltinKey("location.city")!!.id
        val odd = entities.create(w, EntityType.LOCATION, "Ba\"d: Name")
        val town = entities.create(w, EntityType.LOCATION, "Town")
        entities.updateContent(town, "Town", emptyList(), null, null, city, emptyMap())
        relationships.create(w, town, odd, "located_in", "located in", "contains")

        // quote/colon are illegal in vault names, so the wikilink carries the sanitized name (no YAML escaping needed)
        val text = exporter.export(w).content("Locations", "Town.md")
        assertTrue("Located in:\n  - \"[[Ba d Name]]\"\n" in text, text)
    }

    @Test
    fun `link fields respect direction and target filters`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")
        val charSheet = templates.byBuiltinKey("character")!!.id
        val factionSheet = templates.byBuiltinKey("faction")!!.id
        val guild = entities.create(w, EntityType.FACTION, "The Guild")
        entities.updateContent(guild, "The Guild", emptyList(), null, null, factionSheet, emptyMap())
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        entities.updateContent(kaede, "Kaede", emptyList(), null, null, charSheet, emptyMap())
        relationships.create(w, kaede, guild, "member_of", "member of", "has member")

        val files = exporter.export(w)
        // IN field populated from a character -> faction row
        val guildText = files.content("Factions", "The Guild.md")
        assertTrue("Members:\n  - \"[[Kaede]]\"\n" in guildText, guildText)
        // OUT field on the faction ignores that incoming row (kind matches, direction does not)
        assertTrue("Allies:" !in guildText && "Headquarters:" !in guildText)
        // target filter: under Faction, not under Race (which wants a concept.race)
        val kaedeText = files.content("Characters", "Kaede.md")
        assertTrue("Faction:\n  - \"[[The Guild]]\"\n" in kaedeText, kaedeText)
        assertTrue("Race:" !in kaedeText, kaedeText)
    }

    @Test
    fun `soft-deleted relationships and targets are not exported as links`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")
        val city = templates.byBuiltinKey("location.city")!!.id
        val town = entities.create(w, EntityType.LOCATION, "Town")
        entities.updateContent(town, "Town", emptyList(), null, null, city, emptyMap())
        val gone = entities.create(w, EntityType.LOCATION, "Gone")
        val other = entities.create(w, EntityType.LOCATION, "Other")
        val deadRel = relationships.create(w, town, other, "located_in", "located in", "contains")
        relationships.create(w, town, gone, "located_in", "located in", "contains")
        relationships.softDelete(deadRel)
        entities.softDelete(gone)

        val text = exporter.export(w).content("Locations", "Town.md")
        assertTrue("Located in:" !in text, text)
        assertTrue("[[" !in text, text)
    }
}
