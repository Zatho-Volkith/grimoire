package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.db.AppearancesForEntity
import dev.grimoire.db.Entity
import dev.grimoire.db.Template
import dev.grimoire.db.VariantsForEntity
import dev.grimoire.db.Work
import dev.grimoire.db.RelationshipsFor
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.json.JsonStringMap
import dev.grimoire.json.JsonStrings
import dev.grimoire.template.TemplateRepository
import dev.grimoire.variant.VariantRepository
import dev.grimoire.wiki.CanonSnapshot
import dev.grimoire.wiki.WikiConnector
import dev.grimoire.work.WorkRepository
import dev.grimoire.relationship.RelationshipKind
import dev.grimoire.relationship.RelationshipRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    private val templates: TemplateRepository,
    private val works: WorkRepository,
    private val variantsRepo: VariantRepository,
    private val wiki: WikiConnector,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var worldId: String = ""; private set
    var name by mutableStateOf(""); private set
    var aliasesText by mutableStateOf(""); private set
    var summary by mutableStateOf(""); private set
    var body by mutableStateOf(""); private set
    var templateId by mutableStateOf<String?>(null); private set
    var attributes by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var dirty by mutableStateOf(false); private set

    // Canon layer (wiki-sourced entities only). The connector owns it; we just display it.
    var canon by mutableStateOf<CanonSnapshot?>(null); private set
    var sourceLabel by mutableStateOf<String?>(null); private set
    var canonStatus by mutableStateOf<String?>(null); private set

    /** True when the user's text has drifted from what the wiki currently says. */
    val differsFromCanon: Boolean
        get() = canon?.let { c ->
            name != c.name || summary != c.summary.orEmpty() || body != c.body.orEmpty() ||
                c.attributes.any { (k, v) -> attributes[k].orEmpty() != v }
        } ?: false

    var showCanonDiff by mutableStateOf(false); private set
    fun openCanonDiff() { showCanonDiff = true }
    fun closeCanonDiff() { showCanonDiff = false }

    // Take-canon actions route through edit {} so they save like any other keystroke.
    fun takeCanonName() { canon?.let { c -> edit { name = c.name } } }
    fun takeCanonSummary() { canon?.let { c -> edit { summary = c.summary.orEmpty() } } }
    fun takeCanonBody() { canon?.let { c -> edit { body = c.body.orEmpty() } } }
    fun takeCanonAttribute(key: String) {
        canon?.attributes?.get(key)?.let { v -> edit { attributes = attributes + (key to v) } }
    }

    /** Templates this entity could use (its type, its world). Populated by [load]. */
    var templateOptions: Flow<List<Template>> = flowOf(emptyList()); private set

    /** Works of this world, for the variant scope picker. Populated by [load]. */
    var workOptions: Flow<List<Work>> = flowOf(emptyList()); private set

    private var saveJob: Job? = null

    suspend fun load() {
        val e = repo.entity(id).first() ?: return
        worldId = e.world_id
        name = e.name
        aliasesText = JsonStrings.decode(e.aliases).joinToString(", ")
        summary = e.summary.orEmpty()
        body = e.body.orEmpty()
        templateId = e.template_id
        attributes = JsonStringMap.decode(e.attributes)
        templateOptions = templates.forType(worldId, EntityType.fromKey(e.type))
        workOptions = works.forWorld(worldId)
        val source = repo.sourceById(e.source_id)
        if (source?.kind == "fandom_wiki") {
            canon = CanonSnapshot.decode(e.canon_snapshot)
            sourceLabel = source.label
        }
        loaded = true
    }

    /** Re-fetches the wiki page; only the canon layer can change. Degrades to a status line offline. */
    fun refreshCanon() {
        scope.launch {
            canonStatus = "Checking…"
            canonStatus = runCatching { wiki.refresh(id) }.fold(
                onSuccess = { result ->
                    when (result) {
                        WikiConnector.RefreshResult.UPDATED -> {
                            canon = CanonSnapshot.decode(repo.entity(id).first()?.canon_snapshot)
                            "Canon updated"
                        }
                        WikiConnector.RefreshResult.UNCHANGED -> "Canon up to date"
                        WikiConnector.RefreshResult.NOT_WIKI_SOURCED -> null
                    }
                },
                onFailure = { "Refresh failed — offline?" },
            )
        }
    }

    fun editName(v: String) = edit { name = v }
    fun editAliasesText(v: String) = edit { aliasesText = v }
    fun editSummary(v: String) = edit { summary = v }
    fun editBody(v: String) = edit { body = v }
    fun selectTemplate(id: String?) = edit { templateId = id }
    /** Attribute values survive template switches; only the visible fields change. */
    fun editAttribute(key: String, v: String) = edit { attributes = attributes + (key to v) }

    // --- Relationships: written straight through (no debounce), so no flush involvement.

    val relationshipList: Flow<List<RelationshipsFor>> = relationships.forEntity(id)

    /** Chapters that cast this entity, managed from the chapter side; read-only here. */
    val appearances: Flow<List<AppearancesForEntity>> = works.appearances(id)

    // --- variants: list is live; one variant's overrides edit through a sub-editor.

    val variants: Flow<List<VariantsForEntity>> = variantsRepo.forEntity(id)
    var variantEditor by mutableStateOf<VariantEditorState?>(null); private set

    fun openVariant(variantId: String) {
        if (variantEditor?.id == variantId) return
        val previous = variantEditor
        val next = VariantEditorState(variantId, variantsRepo, scope)
        variantEditor = next
        scope.launch {
            previous?.flush()
            next.load()
        }
    }

    fun closeVariant() {
        val previous = variantEditor ?: return
        variantEditor = null
        scope.launch { previous.flush() }
    }

    /** Creates a variant scoped to [workId] (or, with null, to [continuityKey]) and opens it. */
    fun addVariant(workId: String?, continuityKey: String?) {
        scope.launch {
            variantsRepo.create(id, workId, continuityKey)?.let { openVariant(it) }
        }
    }

    fun removeVariant(variantId: String) {
        if (variantEditor?.id == variantId) variantEditor = null
        scope.launch { variantsRepo.softDelete(variantId) }
    }

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
        variantEditor?.flush()
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
                templateId = templateId,
                attributes = attributes.filterValues { it.isNotBlank() },
            )
        }
    }

    companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
