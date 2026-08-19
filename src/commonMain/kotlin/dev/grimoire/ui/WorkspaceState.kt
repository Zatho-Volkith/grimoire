package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.grimoire.db.Entity
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.relationship.RelationshipRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** One open world: which type is browsed, what's searched, which entity is being edited. */
class WorkspaceState(
    val worldId: String,
    private val entities: EntityRepository,
    private val relationships: RelationshipRepository,
    private val scope: CoroutineScope,
) {
    var type by mutableStateOf(EntityType.CHARACTER); private set
    var search by mutableStateOf(""); private set
    var editor by mutableStateOf<EntityEditorState?>(null); private set

    /** The list pane: search hits when a query is present, else the selected type. Live. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val items: Flow<List<Entity>> =
        snapshotFlow { type to search }
            .debounce { (_, q) -> if (q.isBlank()) 0L else 150L }
            .flatMapLatest { (t, q) -> if (q.isBlank()) entities.byType(worldId, t) else entities.search(worldId, q) }

    fun selectType(t: EntityType) { type = t; search = "" }
    fun searchFor(q: String) { search = q }

    fun open(id: String) {
        if (editor?.id == id) return
        val previous = editor
        val next = EntityEditorState(id, entities, relationships, scope)
        editor = next
        scope.launch {
            previous?.flush()
            next.load()
        }
    }

    fun close() {
        val previous = editor ?: return
        editor = null
        scope.launch { previous.flush() }
    }

    fun createEntity() {
        scope.launch { open(entities.create(worldId, type, "New ${type.label}")) }
    }

    fun deleteOpenEntity() {
        val target = editor ?: return
        editor = null
        scope.launch { entities.softDelete(target.id) }
    }

    suspend fun flush() { editor?.flush() }
}
