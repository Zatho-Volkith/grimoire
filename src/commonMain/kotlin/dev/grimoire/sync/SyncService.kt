package dev.grimoire.sync

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.id.IdGenerator
import dev.grimoire.wiki.WikiConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drains the `sync_queue`: wiki canon refreshes happen in the background so the manual
 * button becomes a fallback, not a chore. Offline-first rules apply throughout — every
 * failure is just a retry scheduled with exponential backoff, never an error the user sees.
 *
 * Cadence: on [start] and every [pollMillis], entities whose wiki source is older than
 * [maxAgeMillis] are enqueued (deduplicated), then pending items are processed with
 * [politeDelayMillis] between fetches so we're a considerate API citizen.
 */
class SyncService(
    private val db: GrimoireDatabase,
    private val ids: IdGenerator,
    private val clock: () -> Long,
    private val wiki: WikiConnector,
    private val maxAgeMillis: Long = 24 * 60 * 60 * 1000L,
    private val pollMillis: Long = 15 * 60 * 1000L,
    private val politeDelayMillis: Long = 2_000L,
) {
    private val q get() = db.grimoireQueries

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            enqueueStaleRefreshes()
            drainPending()
            delay(pollMillis)
        }
    }

    /** Queues a refresh for every live wiki-sourced entity gone stale. Returns how many were added. */
    suspend fun enqueueStaleRefreshes(): Int = withContext(Dispatchers.IO) {
        val now = clock()
        var added = 0
        for (entityId in q.entitiesWithStaleWikiSources(now - maxAgeMillis).executeAsList()) {
            if (q.syncItemPending(OPERATION, TARGET_TABLE, entityId).executeAsOne() > 0L) continue
            q.enqueueSync(
                id = ids.next(), operation = OPERATION, target_table = TARGET_TABLE,
                target_id = entityId, payload = null, created_at = now,
            )
            added++
        }
        added
    }

    /** Processes due queue items (respecting per-item retry timers). Returns how many succeeded. */
    suspend fun drainPending(limit: Long = 20): Int = withContext(Dispatchers.IO) {
        val batch = q.nextSyncBatch(clock(), limit).executeAsList()
        var refreshed = 0
        for ((index, item) in batch.withIndex()) {
            if (index > 0) delay(politeDelayMillis)
            runCatching { wiki.refresh(item.target_id) }
                .onSuccess { result ->
                    // NOT_WIKI_SOURCED means the entity vanished or changed hands — drop the work.
                    q.deleteSyncItem(item.id)
                    if (result == WikiConnector.RefreshResult.UPDATED) refreshed++
                }
                .onFailure { failure ->
                    val backoff = (BASE_RETRY_MILLIS shl item.attempts.toInt().coerceAtMost(6))
                        .coerceAtMost(MAX_RETRY_MILLIS)
                    q.markSyncFailure(
                        last_error = failure.message?.take(200), next_retry_at = clock() + backoff, id = item.id,
                    )
                }
        }
        refreshed
    }

    companion object {
        const val OPERATION = "wiki_refresh"
        const val TARGET_TABLE = "entity"
        private const val BASE_RETRY_MILLIS = 5 * 60 * 1000L
        private const val MAX_RETRY_MILLIS = 6 * 60 * 60 * 1000L
    }
}
