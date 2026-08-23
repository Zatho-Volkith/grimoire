package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.ui.EventEditorState
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineRepositoryTest {
    private val ids = IdGenerator(Random(13)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val timeline = TimelineRepository(db, ids) { 1L }

    @Test
    fun `events append in order and move swaps neighbors`() = runTest {
        val w = worlds.create("Arda")
        val e1 = timeline.create(w, "The departure")
        val e2 = timeline.create(w, "The storm")
        val e3 = timeline.create(w, "The return")

        assertEquals(listOf(e1, e2, e3), timeline.events(w).first().map { it.id })

        timeline.move(e3, -1)
        assertEquals(listOf(e1, e3, e2), timeline.events(w).first().map { it.id })

        timeline.move(e1, -1) // already first: no-op
        assertEquals(listOf(e1, e3, e2), timeline.events(w).first().map { it.id })
    }

    @Test
    fun `event content updates and soft delete hides the event`() = runTest {
        val w = worlds.create("Arda")
        val e1 = timeline.create(w, "The departure")

        timeline.updateEvent(e1, "The departure", "She left before sunrise.", "Third Age, 3019", "Third Age")
        val row = timeline.event(e1).first()!!
        assertEquals("Third Age, 3019", row.display_date)
        assertEquals("Third Age", row.era)

        timeline.softDelete(e1)
        assertTrue(timeline.events(w).first().isEmpty())
        assertEquals(1L, timeline.event(e1).first()?.deleted_at)
    }

    @Test
    fun `participants can be removed and re-added, reviving the tombstone`() = runTest {
        val w = worlds.create("Arda")
        val e1 = timeline.create(w, "The duel")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")

        timeline.addParticipant(e1, kaede, "challenger")
        assertEquals(listOf("Kaede" to "challenger"), timeline.participants(e1).first().map { it.name to it.role })

        timeline.removeParticipant(e1, kaede)
        assertTrue(timeline.participants(e1).first().isEmpty())

        timeline.addParticipant(e1, kaede, "witness") // same PK: revived, not duplicated
        assertEquals(listOf("Kaede" to "witness"), timeline.participants(e1).first().map { it.name to it.role })
    }

    @Test
    fun `participants of soft-deleted entities drop out`() = runTest {
        val w = worlds.create("Arda")
        val e1 = timeline.create(w, "The duel")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        timeline.addParticipant(e1, kaede, null)

        entities.softDelete(kaede)
        assertTrue(timeline.participants(e1).first().isEmpty())
    }

    @Test
    fun `event editor flush persists pending edits`() = runTest {
        val w = worlds.create("Arda")
        val e1 = timeline.create(w, "New event")

        val editor = EventEditorState(e1, timeline, entities, backgroundScope)
        editor.load()
        editor.editTitle("The landing")
        editor.editDisplayDate("Year 1")
        editor.flush()

        val row = timeline.event(e1).first()!!
        assertEquals("The landing", row.title)
        assertEquals("Year 1", row.display_date)
    }

    @Test
    fun `calendars keep independent event lists and orderings`() = runTest {
        val w = worlds.create("Arda")
        val worldE = timeline.create(w, "The Sundering")
        val storyE = timeline.create(w, "Chapter one happens", "Story order")
        timeline.create(w, "The Return", "Story order")

        assertEquals(listOf("The Sundering"), timeline.events(w).first().map { it.title })
        assertEquals(
            listOf("Chapter one happens", "The Return"),
            timeline.events(w, "Story order").first().map { it.title },
        )
        assertEquals(listOf("Story order", "default"), timeline.calendars(w).first())

        timeline.move(storyE, 1)
        assertEquals(
            listOf("The Return", "Chapter one happens"),
            timeline.events(w, "Story order").first().map { it.title },
        )
        assertEquals(listOf("The Sundering"), timeline.events(w).first().map { it.title }, "default untouched")
        assertEquals(1L, timeline.event(worldE).first()!!.sort_position)
    }
}
