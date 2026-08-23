package dev.grimoire

import dev.grimoire.entity.EntityType
import dev.grimoire.settings.SettingsStore
import dev.grimoire.template.BuiltinTemplates
import dev.grimoire.work.Ao3Markdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorialWorldTest {
    private val settings = object : SettingsStore {
        override fun load() = emptyMap<String, String>()
        override fun save(values: Map<String, String>) = Unit
    }

    @Test
    fun `the tutorial world arrives complete and every chapter renders`() = runBlocking {
        val session = testSession(settings)
        // Main seeds built-ins before any world exists; mirror that.
        // (reaching into the session's repos the same way the UI would)
        session.createTutorialWorld()
        withTimeout(10_000) { while (session.workspace == null) delay(20) }
        val w = session.workspace!!.worldId
        assertEquals("Grimoire Tutorial", session.worlds.world(w).first()!!.name)

        // Cast of examples, filed and connected.
        val characters = session.entities.byType(w, EntityType.CHARACTER).first()
        assertEquals(setOf("Mira Vale", "Josselin Reed"), characters.map { it.name }.toSet())
        val mira = characters.first { it.name == "Mira Vale" }
        assertEquals(2, session.relationships.forEntity(mira.id).first().size)
        assertEquals(
            "Headcanon",
            session.entities.sourceById(characters.first { it.name == "Josselin Reed" }.source_id)!!.label,
        )

        // The guide: six chapters, real Markdown, AO3-convertible without blowing up.
        val tour = session.works.forWorld(w).first().single()
        val chapters = session.works.chapters(tour.id).first()
        assertEquals(
            listOf("Start here", "Writing", "Entities & connections", "Timelines", "Canon from wikis", "Variants, export, goodbye"),
            chapters.map { it.title },
        )
        chapters.forEach { c ->
            assertTrue(c.word_count > 30, "${c.title} has substance")
            assertTrue(Ao3Markdown.toHtml(c.body).contains("<p>"), "${c.title} converts")
        }
        assertEquals(2, session.works.cast(chapters[2].id).first().size, "chapter 3 has its cast")

        // Two calendars; the variant demo is in place.
        assertEquals(listOf("Story order", "default"), session.timeline.calendars(w).first())
        assertEquals(3, session.timeline.events(w).first().size)
        assertEquals(2, session.timeline.events(w, "Story order").first().size)
        assertTrue(mira.template_id != null, "Mira uses the built-in Character sheet")
        val variant = session.variants.forEntity(mira.id).first().single()
        assertEquals("Mira the Unmapped", variant.name_override)
        assertEquals("""{"age":"31"}""", variant.attribute_patch)
    }
}
