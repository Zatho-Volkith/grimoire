package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppearanceTest {
    private val ids = IdGenerator(Random(37)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val works = WorkRepository(db, ids) { 1L }

    @Test
    fun `cast members can be removed and re-added, reviving the tombstone`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id

        works.addAppearance(c1, kaede)
        assertEquals(listOf("Kaede"), works.cast(c1).first().map { it.name })

        works.removeAppearance(c1, kaede)
        assertTrue(works.cast(c1).first().isEmpty())

        works.addAppearance(c1, kaede) // same PK: revived, not duplicated
        assertEquals(listOf("Kaede"), works.cast(c1).first().map { it.name })
    }

    @Test
    fun `an entity lists its appearances with work and chapter labels, in reading order`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        val work = works.create(w, "The Long Road")
        val c1 = works.chapters(work).first().single().id
        works.updateChapter(c1, "Departure", "")
        val c2 = works.addChapter(work)

        works.addAppearance(c2, kaede)
        works.addAppearance(c1, kaede)

        val rows = works.appearances(kaede).first()
        assertEquals(listOf(c1, c2), rows.map { it.chapter_id }, "reading order, not insertion order")
        assertEquals("The Long Road", rows.first().work_title)
        assertEquals("Departure", rows.first().chapter_title)
    }

    @Test
    fun `appearances hide with their chapter, work, or entity and come back on restore`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id
        works.addAppearance(c1, kaede)

        works.softDeleteChapter(c1)
        assertTrue(works.appearances(kaede).first().isEmpty())
        works.restoreChapter(c1)
        assertEquals(1, works.appearances(kaede).first().size)

        entities.softDelete(kaede)
        assertTrue(works.cast(c1).first().isEmpty())
        entities.restore(kaede)
        assertEquals(1, works.cast(c1).first().size)
    }
}
