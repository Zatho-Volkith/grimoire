package dev.grimoire.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

/**
 * Fire-and-forget flushes of editors that were just closed or replaced. [join] lets the owner's
 * own flush wait for them, so quitting right after switching editors can't cut a save off.
 */
@OptIn(ExperimentalAtomicApi::class)
class PendingWork(private val scope: CoroutineScope) {
    private val jobs = AtomicReference<List<Job>>(emptyList())

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        val job = scope.launch(block = block)
        jobs.update { list -> list.filter { it.isActive } + job }
    }

    suspend fun join() {
        jobs.load().joinAll()
    }
}
