@file:OptIn(InternalComposeUiApi::class)

package dev.grimoire

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.export.ExportSink
import dev.grimoire.export.WorldExporter
import dev.grimoire.id.IdGenerator
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.TemplateRepository
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.variant.VariantRepository
import dev.grimoire.ui.Session
import dev.grimoire.ui.WorkspaceSection
import dev.grimoire.ui.handleShortcut
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** State-holder-level tests for the shortcut dispatch — no Compose tree needed. */
class ShortcutsTest {
    private val ids = IdGenerator(Random(29), System::currentTimeMillis)
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids, System::currentTimeMillis)
    private val entities = EntityRepository(db, ids, System::currentTimeMillis)
    private val settings = object : dev.grimoire.settings.SettingsStore {
        var saved = mapOf<String, String>()
        override fun load() = saved
        override fun save(values: Map<String, String>) { saved = values }
    }
    private val session = Session(
        worlds, entities,
        RelationshipRepository(db, ids, System::currentTimeMillis),
        WorkRepository(db, ids, System::currentTimeMillis),
        TimelineRepository(db, ids, System::currentTimeMillis),
        TemplateRepository(db, ids, System::currentTimeMillis),
        VariantRepository(db, ids, System::currentTimeMillis),
        dev.grimoire.wiki.WikiConnector(db, ids, System::currentTimeMillis, dev.grimoire.net.Fetcher { error("no network in tests") }),
        WorldExporter(db), ExportSink { _, _ -> null }, settings, dev.grimoire.sync.SyncStatus(),
        dev.grimoire.update.UpdateChecker({ error("no network in tests") }, "0.0.0"), noopUpdateLauncher,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private fun press(key: Key, ctrl: Boolean = true): Boolean =
        handleShortcut(session, KeyEvent(key = key, type = KeyEventType.KeyDown, isCtrlPressed = ctrl))

    private fun await(what: String, condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    @Test
    fun `ctrl+1-4 switch sections, ctrl+N creates in the section, esc closes its editor`() {
        session.openWorld(runBlocking { worlds.create("Arda") })
        val ws = session.workspace!!

        assertTrue(press(Key.Two))
        assertEquals(WorkspaceSection.WORKS, ws.section)
        assertTrue(press(Key.N))
        await("work editor") { ws.workEditor?.loaded == true }
        assertTrue(press(Key.Escape, ctrl = false))
        assertNull(ws.workEditor)

        assertTrue(press(Key.Three))
        assertEquals(WorkspaceSection.TIMELINE, ws.section)
        assertTrue(press(Key.N))
        await("event editor") { ws.eventEditor?.loaded == true }

        assertTrue(press(Key.Four))
        assertEquals(WorkspaceSection.TEMPLATES, ws.section)

        assertTrue(press(Key.One))
        assertEquals(WorkspaceSection.ENTITIES, ws.section)

        assertTrue(press(Key.F))
        assertEquals(1, ws.focusSearchTick)
    }

    @Test
    fun `ctrl+S flushes the dirty editor immediately`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        session.openWorld(w)
        session.workspace!!.open(kaede)
        await("editor") { session.workspace?.editor?.loaded == true }

        session.workspace!!.editor!!.editName("Kaede of the North")
        assertTrue(press(Key.S))
        await("flush") { runBlocking { entities.entity(kaede).first() }!!.name == "Kaede of the North" }
        assertFalse(session.workspace!!.editor!!.dirty)
    }

    @Test
    fun `key-up events, unknown keys, and the world picker are ignored`() {
        assertFalse(press(Key.Two), "no workspace open yet")
        session.openWorld(runBlocking { worlds.create("Arda") })
        assertFalse(handleShortcut(session, KeyEvent(key = Key.S, type = KeyEventType.KeyUp, isCtrlPressed = true)))
        assertFalse(press(Key.Z))
        assertFalse(press(Key.Escape), "ctrl+esc is no shortcut - esc only counts unmodified")
    }
}
