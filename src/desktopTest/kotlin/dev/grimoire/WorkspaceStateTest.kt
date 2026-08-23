package dev.grimoire

import dev.grimoire.entity.EntityType
import dev.grimoire.settings.SettingsStore
import dev.grimoire.ui.WorkspaceSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/** Navigation/flush semantics of the workspace state holder — no Compose tree needed. */
class WorkspaceStateTest {
    private val settings = object : SettingsStore {
        override fun load() = emptyMap<String, String>()
        override fun save(values: Map<String, String>) = Unit
    }
    private val session = testSession(settings)

    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    @Test
    fun `opening an already-open entity from another section still switches to Entities`() {
        session.openWorld(runBlocking { session.worlds.create("Arda") })
        val ws = session.workspace!!
        val kaede = runBlocking { session.entities.create(ws.worldId, EntityType.CHARACTER, "Kaede") }

        ws.open(kaede)
        await { ws.editor?.loaded == true }
        ws.selectWorks()
        assertEquals(WorkspaceSection.WORKS, ws.section)

        ws.open(kaede) // same id: no reload, but the section must still change
        assertEquals(WorkspaceSection.ENTITIES, ws.section)
        assertEquals(kaede, ws.editor?.id)
    }

    @Test
    fun `switching works flushes the dirty chapter editor of the previous one`() {
        session.openWorld(runBlocking { session.worlds.create("Arda") })
        val ws = session.workspace!!
        val w1 = runBlocking { session.works.create(ws.worldId, "First") }
        val w2 = runBlocking { session.works.create(ws.worldId, "Second") }
        val c1 = runBlocking { session.works.chapters(w1).first() }.single().id

        ws.openWork(w1)
        await { ws.workEditor?.chapterEditor?.loaded == true }
        ws.workEditor!!.chapterEditor!!.editBody("She left at dawn.")

        ws.openWork(w2) // flushes the whole previous work editor, chapter included
        await { runBlocking { session.works.chapter(c1).first() }?.body == "She left at dawn." }
        assertEquals(4L, runBlocking { session.works.chapter(c1).first() }!!.word_count)
    }
}
