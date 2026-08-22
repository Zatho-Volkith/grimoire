package dev.grimoire

import dev.grimoire.settings.SettingsStore
import dev.grimoire.ui.AppTheme
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeSettingsTest {

    @Test
    fun `unknown or missing keys fall back to the Grimoire default`() {
        assertEquals(AppTheme.GRIMOIRE, AppTheme.fromKey(null))
        assertEquals(AppTheme.GRIMOIRE, AppTheme.fromKey("neon"))
        assertEquals(AppTheme.PARCHMENT, AppTheme.fromKey("parchment"))
    }

    @Test
    fun `file store round-trips and tolerates a missing or corrupt file`() {
        val file = createTempDirectory("grimoire-test").resolve("settings.json")
        val store = FileSettingsStore(file)
        assertEquals(emptyMap(), store.load(), "missing file reads as empty")

        store.save(mapOf("theme" to "midnight", "future" to "kept"))
        assertEquals(mapOf("theme" to "midnight", "future" to "kept"), FileSettingsStore(file).load())

        file.toFile().writeText("{corrupt")
        assertEquals(emptyMap(), store.load(), "corrupt file reads as empty, not a crash")
    }

    @Test
    fun `selecting a theme persists it for the next session`() {
        val store = object : SettingsStore {
            var saved = mapOf("keepme" to "1")
            override fun load() = saved
            override fun save(values: Map<String, String>) { saved = values }
        }
        val session = testSession(store)
        assertEquals(AppTheme.GRIMOIRE, session.theme)

        session.selectTheme(AppTheme.PARCHMENT)
        assertEquals("parchment", store.saved["theme"])
        assertEquals("1", store.saved["keepme"], "other settings survive a theme change")
        assertEquals(AppTheme.PARCHMENT, testSession(store).theme, "a fresh session restores it")
    }
}
