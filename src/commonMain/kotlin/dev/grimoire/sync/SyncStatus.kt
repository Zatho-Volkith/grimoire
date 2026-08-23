package dev.grimoire.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Observable snapshot of background canon work, written by [SyncService], read by the header.
 * Deliberately tiny: writers only ever need "is anything queued" and "has anything happened".
 */
class SyncStatus {
    /** Queue depth, including items waiting out a retry backoff. */
    var pending by mutableStateOf(0)

    /** Pages whose canon actually changed since the app started. */
    var refreshedThisSession by mutableStateOf(0)

    /** Null until the first check completes. */
    var lastCheckedAt by mutableStateOf<Long?>(null)

    /** One short line for the header, or null when there is nothing worth saying. */
    val headline: String?
        get() = when {
            pending > 0 -> "Canon: $pending queued"
            refreshedThisSession > 0 -> "Canon: $refreshedThisSession page(s) refreshed"
            else -> null
        }
}
