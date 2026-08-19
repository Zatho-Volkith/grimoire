package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.db.Entity
import dev.grimoire.db.RelationshipsFor
import dev.grimoire.entity.EntityRepository
import dev.grimoire.json.JsonStrings
import dev.grimoire.relationship.RelationshipKind
import dev.grimoire.relationship.RelationshipRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editing session for one entity. Edits land in local state immediately and are written back
 * after [SAVE_DELAY_MS] of quiet; [flush] writes synchronously-in-suspend for navigation and exit,
 * so a debounce can never lose the last keystrokes.
 */
class EntityEditorState(
    val id: String,
    private val repo: EntityRepository,
    private val relationships: RelationshipRepository,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var worldId: String = ""; private set
    var name by mutableStateOf(""); private set
    var aliasesText by mutableStateOf(""); private set
    var summary by mutableStateOf(""); private set
    var body by mutableStateOf(""); private set
    var dirty by mutableStateOf(false); private set

    private var saveJob: Job? = null

    suspend fun load() {
        val e = repo.entity(id).first() ?: return
        worldId = e.world_id
        name = e.name
        aliasesText = JsonStrings.decode(e.aliases).joinToString(", ")
        summary = e.summary.orEmpty()
        body = e.body.orEmpty()
        loaded = true
    }

    fun editName(v: String) = edit { name = v }
    fun editAliasesText(v: String) = edit { aliasesText = v }
    fun editSummary(v: String) = edit { summary = v }
    fun editBody(v: String) = edit { body = v }

    // --- Relationships: written straight through (no debounce), so no flush involvement.

    val relationshipList: Flow<List<RelationshipsFor>> = relationships.forEntity(id)

    /** Candidate targets for a new relationship: live search over the world, minus this entity. */
    fun searchTargets(text: String): Flow<List<Entity>> =
        repo.search(worldId, text).map { list -> list.filter { it.id != id } }

    fun addRelationship(targetId: String, kind: RelationshipKind, label: String, inverseLabel: String) {
        scope.launch {
            relationships.create(worldId, fromEntity = id, toEntity = targetId, kind = kind.key, label = label, inverseLabel = inverseLabel)
        }
    }

    fun removeRelationship(relationshipId: String) {
        scope.launch { relationships.softDelete(relationshipId) }
    }

    private fun edit(change: () -> Unit) {
        change()
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch { delay(SAVE_DELAY_MS); save() }
    }

    /** Persists pending edits now. Safe to call when nothing is dirty. */
    suspend fun flush() {
        // Cancels a save still waiting out its delay; waits for one already writing (the write
        // itself is NonCancellable, so cancelling mid-write can't drop the edit that cleared `dirty`).
        saveJob?.cancelAndJoin()
        save()
    }

    private suspend fun save() {
        if (!dirty) return
        dirty = false
        withContext(NonCancellable) {
            repo.updateContent(
                id = id,
                name = name.trim().ifEmpty { "Untitled" },
                aliases = aliasesText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                summary = summary.ifBlank { null },
                body = body.ifBlank { null },
            )
        }
    }

    companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
