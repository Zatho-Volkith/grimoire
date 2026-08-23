package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.net.Fetcher
import dev.grimoire.sync.SyncService
import dev.grimoire.wiki.CanonSnapshot
import dev.grimoire.wiki.WikiConnector
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncServiceTest {
    private val fixture: String =
        javaClass.classLoader.getResourceAsStream("teleportation.json")!!.readBytes().decodeToString()
    private val fixtureV2: String = fixture
        .replace("\"revid\":2918967", "\"revid\":3000001")
        .replace("instantaneously move from one location to another", "instantly relocate from one location to another")

    private var served: String = fixture
    private var offline = false
    private val fetcher = Fetcher { _ ->
        check(!offline) { "offline" }
        served
    }

    private var now = 1_000_000L
    private val hour = 60 * 60 * 1000L
    private val ids = IdGenerator(Random(53)) { now }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { now }
    private val entities = EntityRepository(db, ids) { now }
    private val wiki = WikiConnector(db, ids, { now }, fetcher)
    private val sync = SyncService(db, ids, { now }, wiki, politeDelayMillis = 0)

    private suspend fun importedEntity(): String {
        val w = worlds.create("W")
        return wiki.import(w, "powerlisting.fandom.com", "Teleportation", EntityType.CONCEPT)
    }

    private fun queueSize(): Long =
        db.grimoireQueries.nextSyncBatch(Long.MAX_VALUE, 100).executeAsList().size.toLong()

    @Test
    fun `stale sources are enqueued once and a drain refreshes their canon`() = runTest {
        val id = importedEntity()
        assertEquals(0, sync.enqueueStaleRefreshes(), "freshly imported = not stale")

        now += 25 * hour
        assertEquals(1, sync.enqueueStaleRefreshes())
        assertEquals(0, sync.enqueueStaleRefreshes(), "already queued = deduplicated")

        served = fixtureV2
        assertEquals(1, sync.drainPending())
        assertEquals(0L, queueSize(), "completed work leaves the queue")

        val canon = CanonSnapshot.decode(entities.entity(id).first()!!.canon_snapshot)!!
        assertTrue(canon.summary!!.contains("instantly relocate"))
        assertEquals(0, sync.enqueueStaleRefreshes(), "refresh reset the staleness clock")
    }

    @Test
    fun `failures back off and retry when their timer comes due`() = runTest {
        importedEntity()
        now += 25 * hour
        sync.enqueueStaleRefreshes()

        offline = true
        assertEquals(0, sync.drainPending())
        val item = db.grimoireQueries.nextSyncBatch(Long.MAX_VALUE, 10).executeAsList().single()
        assertEquals(1L, item.attempts)
        assertEquals("offline", item.last_error)
        assertTrue(item.next_retry_at!! > now, "retry scheduled in the future")

        assertEquals(0, sync.drainPending(), "not due yet: item is skipped, not retried")
        assertEquals(1L, item.attempts, "no extra attempt burned")

        offline = false
        served = fixtureV2
        now = item.next_retry_at!! + 1
        assertEquals(1, sync.drainPending(), "due again: retried and succeeded")
        assertEquals(0L, queueSize())
    }

    @Test
    fun `queue items for deleted entities are dropped, not retried forever`() = runTest {
        val id = importedEntity()
        now += 25 * hour
        sync.enqueueStaleRefreshes()
        entities.softDelete(id)

        assertEquals(0, sync.drainPending(), "refresh reports NOT_WIKI_SOURCED")
        assertEquals(0L, queueSize(), "the dead item is discarded")
    }
}
