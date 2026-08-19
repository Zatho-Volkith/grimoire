package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.entity.EntityRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Top-level app state: which world is open. Owns the repositories the screens need. */
class Session(
    val worlds: WorldRepository,
    val entities: EntityRepository,
    private val scope: CoroutineScope,
) {
    var workspace by mutableStateOf<WorkspaceState?>(null); private set

    fun openWorld(id: String) { workspace = WorkspaceState(id, entities, scope) }

    fun closeWorld() {
        val ws = workspace ?: return
        workspace = null
        scope.launch { ws.flush() }
    }

    fun createWorld(name: String) {
        scope.launch { openWorld(worlds.create(name)) }
    }

    /** Call before the process exits so a pending debounced save is not lost. */
    suspend fun flush() { workspace?.flush() }
}
