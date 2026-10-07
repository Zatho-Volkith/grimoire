package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.grimoire.ui.ChapterEditorState
import dev.grimoire.work.WorkRepository
import dev.grimoire.work.WorkStatus
import dev.grimoire.entity.EntityRepository
import dev.grimoire.world.WorldRepository
import dev.grimoire.id.IdGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkRepositoryTest {
    private val ids = IdGenerator(Random(7)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val works = WorkRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }

    @Test
    fun `a new work starts as a draft with one empty chapter`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "The Long Road")

        val row = works.work(work).first()!!
        assertEquals("The Long Road" to WorkStatus.DRAFT.key, row.title to row.status)

        val chapters = works.chapters(work).first()
        assertEquals(1, chapters.size)
        assertEquals("" to 0L, chapters.single().body to chapters.single().word_count)
    }

    @Test
    fun `chapters append in order and word counts ignore markdown furniture`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id
        val c2 = works.addChapter(work)

        works.updateChapter(c1, "Departure", "She left at dawn.\n\n---\n\nThe road was *long*.")
        works.updateChapter(c2, null, "")

        val chapters = works.chapters(work).first()
        assertEquals(listOf(c1, c2), chapters.map { it.id })
        assertEquals(8L, chapters[0].word_count) // `---` is not a word; `*long*.` is one
        assertEquals("Departure", chapters[0].title)
        assertNull(chapters[1].title)
    }

    @Test
    fun `moving a chapter swaps it with its neighbor and is a no-op at the edges`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id
        val c2 = works.addChapter(work)
        val c3 = works.addChapter(work)

        works.moveChapter(c3, -1)
        assertEquals(listOf(c1, c3, c2), works.chapters(work).first().map { it.id })

        works.moveChapter(c1, -1) // already first
        assertEquals(listOf(c1, c3, c2), works.chapters(work).first().map { it.id })

        works.moveChapter(c1, 1)
        assertEquals(listOf(c3, c1, c2), works.chapters(work).first().map { it.id })
    }

    @Test
    fun `soft-deleted chapters vanish but their position is never reused`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id
        val c2 = works.addChapter(work)

        works.softDeleteChapter(c2)
        assertEquals(listOf(c1), works.chapters(work).first().map { it.id })

        val c3 = works.addChapter(work)
        val positions = works.chapters(work).first().map { it.sort_position }
        assertEquals(listOf(1L, 3L), positions) // c3 sits after the tombstoned slot
        assertEquals(c3, works.chapters(work).first().last().id)
    }

    @Test
    fun `soft-deleted works drop out of the world list`() = runTest {
        val w = worlds.create("Arda")
        val keep = works.create(w, "Keep")
        val drop = works.create(w, "Drop")

        works.softDeleteWork(drop)
        assertEquals(listOf(keep), works.forWorld(w).first().map { it.id })
    }

    @Test
    fun `chapter editor flush persists pending edits`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id

        val editor = ChapterEditorState(c1, works, entities, w, backgroundScope)
        editor.load()
        editor.editTitle("Departure")
        editor.editBody("She left at dawn.")
        assertTrue(editor.dirty)
        editor.flush()

        val row = works.chapters(work).first().single()
        assertEquals("Departure", row.title)
        assertEquals("She left at dawn.", row.body)
        assertEquals(4L, row.word_count)
        assertEquals(4L, editor.wordCount)
    }

    @Test
    fun `chapter editor tracks cursor and replaces blocks`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id
        val editor = ChapterEditorState(c1, works, entities, w, backgroundScope)
        editor.load()

        editor.editBodyValue(TextFieldValue("one two", TextRange(3)))
        assertEquals(TextRange(3), editor.selection)
        assertTrue(editor.dirty)
        editor.flush()

        // Moving the cursor alone is not an edit.
        editor.editBodyValue(TextFieldValue("one two", TextRange(1)))
        assertEquals(TextRange(1), editor.selection)
        assertEquals(false, editor.dirty)

        editor.editBodyValue(TextFieldValue("one two", TextRange(3)))
        editor.replaceTextBlock(null, "::: texts\nhi\n:::")
        editor.flush()
        assertEquals("one\n\n::: texts\nhi\n:::\n\n two", works.chapters(work).first().single().body)
    }

    @Test
    fun `text convo open, save, cancel`() = runTest {
        val w = worlds.create("Arda")
        val work = works.create(w, "Fic")
        val c1 = works.chapters(work).first().single().id
        val body = "Intro.\n\n::: texts Alex\n@me Sam\nSam: hi\n:::\n\nOutro."
        works.updateChapter(c1, null, body)
        val editor = ChapterEditorState(c1, works, entities, w, backgroundScope)
        editor.load()

        // Cancel leaves the body byte-identical and clean.
        editor.editBodyValue(TextFieldValue(body, TextRange(body.indexOf("hi"))))
        editor.openTextConvo(emptyList())
        val opened = editor.textConvo!!
        assertEquals("Alex", opened.title)
        opened.editText(opened.rows.single().key, "changed")
        editor.cancelTextConvo()
        assertNull(editor.textConvo)
        assertEquals(body, editor.body)
        assertEquals(false, editor.dirty)

        // Save replaces only that block.
        editor.openTextConvo(emptyList())
        val convo = editor.textConvo!!
        convo.editText(convo.rows.single().key, "hello")
        editor.saveTextConvo()
        assertNull(editor.textConvo)
        editor.flush()
        assertEquals(body.replace("Sam: hi", "Sam: hello"), works.chapters(work).first().single().body)

        // Outside any block: a fresh conversation inserted at the cursor.
        editor.editBodyValue(TextFieldValue(editor.body, TextRange(editor.body.length)))
        editor.openTextConvo(listOf("Mira"))
        assertEquals("", editor.textConvo!!.title)
        assertEquals(listOf("Mira"), editor.textConvo!!.senderSuggestions)
        editor.textConvo!!.let { it.editText(it.addMessage(), "new") }
        editor.saveTextConvo()
        assertTrue(editor.body.endsWith("Outro.\n\n::: texts\nnew\n:::"), editor.body)
    }
}
