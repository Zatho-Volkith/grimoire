package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
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

class TrashTest {
    private val ids = IdGenerator(Random(31)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val works = WorkRepository(db, ids) { 1L }
    private val timeline = TimelineRepository(db, ids) { 1L }
    private val templates = TemplateRepository(db, ids) { 1L }

    @Test
    fun `deleted entities appear in trash and restore returns them to their list`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")

        entities.softDelete(kaede)
        assertTrue(entities.byType(w, EntityType.CHARACTER).first().isEmpty())
        assertEquals(listOf("Kaede"), entities.deleted(w).first().map { it.name })

        entities.restore(kaede)
        assertEquals(listOf("Kaede"), entities.byType(w, EntityType.CHARACTER).first().map { it.name })
        assertTrue(entities.deleted(w).first().isEmpty())
    }

    @Test
    fun `restoring an entity also brings back its hidden relationships and search hit`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        val rel = dev.grimoire.relationship.RelationshipRepository(db, ids) { 1L }
        val guild = entities.create(w, EntityType.FACTION, "Guild")
        rel.create(w, kaede, guild, "member_of", null, null)

        entities.softDelete(kaede)
        assertTrue(rel.forEntity(guild).first().isEmpty(), "edges to deleted entities hide")
        assertTrue(entities.search(w, "kaede").first().isEmpty())

        entities.restore(kaede)
        assertEquals(1, rel.forEntity(guild).first().size)
        assertEquals(listOf("Kaede"), entities.search(w, "kaede").first().map { it.name })
    }

    @Test
    fun `a restored chapter returns to its original position`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id
        val c2 = works.addChapter(work)
        val c3 = works.addChapter(work)

        works.softDeleteChapter(c2)
        assertEquals(listOf(c1, c3), works.chapters(work).first().map { it.id })
        assertEquals(listOf(c2), works.deletedChapters(w).first().map { it.id })
        assertEquals("Fic", works.deletedChapters(w).first().single().work_title)

        works.restoreChapter(c2)
        assertEquals(listOf(c1, c2, c3), works.chapters(work).first().map { it.id })
    }

    @Test
    fun `works, events, and templates round-trip through the trash`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "Fic")
        val event = timeline.create(w, "The duel")
        val sheet = templates.create(w, EntityType.CHARACTER, "Sheet")

        works.softDeleteWork(work); timeline.softDelete(event); templates.softDelete(sheet)
        assertEquals(listOf("Fic"), works.deletedWorks(w).first().map { it.title })
        assertEquals(listOf("The duel"), timeline.deleted(w).first().map { it.title })
        assertEquals(listOf("Sheet"), templates.deleted(w).first().map { it.name })

        works.restoreWork(work); timeline.restore(event); templates.restore(sheet)
        assertEquals(listOf("Fic"), works.forWorld(w).first().map { it.title })
        assertEquals(listOf("The duel"), timeline.events(w).first().map { it.title })
        assertEquals(listOf("Sheet"), templates.forWorld(w).first().map { it.name })
        assertTrue(works.deletedWorks(w).first().isEmpty() && timeline.deleted(w).first().isEmpty() && templates.deleted(w).first().isEmpty())
    }
}
