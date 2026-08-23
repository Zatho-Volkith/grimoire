package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.json.JsonStringMap
import dev.grimoire.net.Fetcher
import dev.grimoire.wiki.CanonSnapshot
import dev.grimoire.wiki.WikiConnector
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WikiConnectorTest {
    private val fixture: String =
        javaClass.classLoader.getResourceAsStream("teleportation.json")!!.readBytes().decodeToString()

    /** The same page at a newer revision with changed canon text. */
    private val fixtureV2: String = fixture
        .replace("\"revid\":2918967", "\"revid\":3000001")
        .replace("instantaneously move from one location to another", "instantly relocate from one location to another")

    private var served: String = fixture
    private var offline = false
    private val fetcher = Fetcher { url ->
        check(!offline) { "offline" }
        check("powerlisting.fandom.com" in url)
        if ("opensearch" in url) """["tele",["Teleportation","Teleportation Negation"],["",""],["u1","u2"]]"""
        else served
    }

    private val ids = IdGenerator(Random(47)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val wiki = WikiConnector(db, ids, { 1L }, fetcher)

    @Test
    fun `search returns title suggestions`() = runTest {
        assertEquals(listOf("Teleportation", "Teleportation Negation"), wiki.search("powerlisting.fandom.com", "tele"))
    }

    @Test
    fun `import creates a provenanced source and an entity with canon copied into user fields`() = runTest {
        val w = worlds.create("My Original World")
        val id = wiki.import(w, "powerlisting.fandom.com", "Teleportation", EntityType.CONCEPT)

        val e = entities.entity(id).first()!!
        assertEquals("Teleportation" to "concept", e.name to e.type)
        assertTrue(e.summary!!.startsWith("The power to **instantaneously move"))
        assertTrue("## Also Called" in e.body!!)
        assertEquals(
            "Instantly transport oneself from one place to another without traversing the physical space in between",
            JsonStringMap.decode(e.attributes)["Power/Ability to"],
        )

        val canon = CanonSnapshot.decode(e.canon_snapshot)!!
        assertEquals(e.name, canon.name)
        assertEquals(e.summary, canon.summary)

        val source = entities.sourceById(e.source_id)!!
        assertEquals("fandom_wiki", source.kind)
        assertEquals("2918967", source.revision)
        assertEquals("https://powerlisting.fandom.com/wiki/Teleportation", source.remote_url)
        assertEquals(fixture, source.cached_payload)
    }

    @Test
    fun `refresh updates canon but never the user's text`() = runTest {
        val w = worlds.create("W")
        val id = wiki.import(w, "powerlisting.fandom.com", "Teleportation", EntityType.CONCEPT)

        // Same revision: no-op.
        assertEquals(WikiConnector.RefreshResult.UNCHANGED, wiki.refresh(id))

        // The user rewrites their copy; the wiki also moves on.
        entities.updateContent(id, "Teleportation (my take)", emptyList(), "My own spin.", "My headcanon body.", null, emptyMap())
        served = fixtureV2
        assertEquals(WikiConnector.RefreshResult.UPDATED, wiki.refresh(id))

        val e = entities.entity(id).first()!!
        assertEquals("Teleportation (my take)", e.name, "user text untouched")
        assertEquals("My own spin.", e.summary)
        val canon = CanonSnapshot.decode(e.canon_snapshot)!!
        assertTrue(canon.summary!!.contains("instantly relocate"), "canon layer follows the wiki")
        assertEquals("3000001", entities.sourceById(e.source_id)!!.revision)
    }

    @Test
    fun `user-authored entities refuse refresh and offline fails loudly but harmlessly`() = runTest {
        val w = worlds.create("W")
        val handmade = entities.create(w, EntityType.CHARACTER, "Kaede")
        assertEquals(WikiConnector.RefreshResult.NOT_WIKI_SOURCED, wiki.refresh(handmade))

        offline = true
        assertFailsWith<IllegalStateException> { wiki.import(w, "powerlisting.fandom.com", "Teleportation", EntityType.CONCEPT) }
        assertTrue(entities.byType(w, EntityType.CONCEPT).first().isEmpty(), "failed import leaves nothing behind")
    }
}
