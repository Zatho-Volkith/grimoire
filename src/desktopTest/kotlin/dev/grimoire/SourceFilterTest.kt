package dev.grimoire

import dev.grimoire.entity.EntityType
import dev.grimoire.net.Fetcher
import dev.grimoire.settings.SettingsStore
import dev.grimoire.ui.SourceFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceFilterTest {
    private val settings = object : SettingsStore {
        override fun load() = emptyMap<String, String>()
        override fun save(values: Map<String, String>) = Unit
    }
    private val session = testSession(settings)

    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    @Test
    fun `entities file under named sources and the list filter respects them`() = runBlocking {
        session.openWorld(session.worlds.create("Arda"))
        val ws = session.workspace!!
        val w = ws.worldId

        val kaede = session.entities.create(w, EntityType.CHARACTER, "Kaede")
        val rin = session.entities.create(w, EntityType.CHARACTER, "Rin")
        val fanon = session.entities.createSource(w, "Fandom consensus")
        session.entities.setSource(rin, fanon)

        assertEquals(listOf("My notes", "Fandom consensus"), ws.sourceList.first().map { it.label })

        assertEquals(setOf("Kaede", "Rin"), ws.items.first().map { it.name }.toSet(), "no filter")

        ws.filterBySource(SourceFilter.User(fanon, "Fandom consensus"))
        await { runBlocking { ws.items.first() }.map { it.name } == listOf("Rin") }

        ws.filterBySource(SourceFilter.Wiki)
        await { runBlocking { ws.items.first() }.isEmpty() }

        ws.filterBySource(null)
        await { runBlocking { ws.items.first() }.size == 2 }

        session.entities.renameSource(fanon, "Widely accepted fanon")
        await { runBlocking { ws.sourceList.first() }.any { it.label == "Widely accepted fanon" } }
    }

    @Test
    fun `wiki imports show up under the wiki filter`() = runBlocking {
        val fixture = javaClass.classLoader.getResourceAsStream("teleportation.json")!!.readBytes().decodeToString()
        val online = testSession(settings, Fetcher { fixture })
        online.openWorld(online.worlds.create("W2"))
        val ws = online.workspace!!

        online.entities.create(ws.worldId, EntityType.CONCEPT, "Handmade")
        val imported = online.wiki.import(ws.worldId, "powerlisting.fandom.com", "Teleportation", EntityType.CONCEPT)

        ws.selectType(EntityType.CONCEPT)
        ws.filterBySource(SourceFilter.Wiki)
        await { runBlocking { ws.items.first() }.map { it.id } == listOf(imported) }
    }
}
