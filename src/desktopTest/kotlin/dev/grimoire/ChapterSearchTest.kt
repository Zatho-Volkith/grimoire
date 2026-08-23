package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
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

class ChapterSearchTest {
    private val ids = IdGenerator(Random(41)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val works = WorkRepository(db, ids) { 1L }

    @Test
    fun `search finds body and title text, tracks edits, and hides deleted chapters`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "The Long Road")
        val c1 = works.chapters(work).first().single().id
        works.updateChapter(c1, "Departure", "She left at dawn, sword on her back.")
        val c2 = works.addChapter(work)
        works.updateChapter(c2, null, "The storm broke at midnight.")

        assertEquals(listOf(c1), works.searchChapters(w, "sword").first().map { it.id })
        assertEquals(listOf(c1), works.searchChapters(w, "departure").first().map { it.id }, "title matches too")
        assertEquals("The Long Road", works.searchChapters(w, "sword").first().single().work_title)

        works.updateChapter(c1, "Departure", "She stayed home.") // edit drops the old terms
        assertTrue(works.searchChapters(w, "sword").first().isEmpty())
        assertEquals(listOf(c1), works.searchChapters(w, "home").first().map { it.id })

        works.softDeleteChapter(c2)
        assertTrue(works.searchChapters(w, "storm").first().isEmpty(), "deleted chapters hide")
        works.restoreChapter(c2)
        assertEquals(listOf(c2), works.searchChapters(w, "storm").first().map { it.id })

        assertTrue(works.searchChapters(w, "  ").first().isEmpty(), "blank query yields nothing")
    }
}
