package dev.grimoire

import dev.grimoire.entity.EntityType
import dev.grimoire.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class WikiImportInputTest {
    private val settings = object : SettingsStore {
        override fun load() = emptyMap<String, String>()
        override fun save(values: Map<String, String>) = Unit
    }

    @Test
    fun `pasting a full page url fills host and selects the page in one go`() = runBlocking {
        val session = testSession(settings)
        session.openWorld(session.worlds.create("W"))
        val ws = session.workspace!!
        ws.openWikiImport()
        val imp = ws.wikiImport!!

        imp.editHost("https://cyberpunk.fandom.com/wiki/V_(character)")
        assertEquals("cyberpunk.fandom.com", imp.host)
        assertEquals("V (character)", imp.selected, "page title selected straight from the paste")

        imp.editHost("powerlisting.fandom.com")
        assertEquals("powerlisting.fandom.com", imp.host, "bare hosts pass through")

        imp.editHost("http://terraria.wiki.gg/wiki/Zenith_%28seed%29?so=search#Notes")
        assertEquals("terraria.wiki.gg", imp.host)
        assertEquals("Zenith (seed)", imp.selected, "percent-encoding and fragments handled")
        assertEquals(EntityType.CONCEPT, imp.type)
    }
}
