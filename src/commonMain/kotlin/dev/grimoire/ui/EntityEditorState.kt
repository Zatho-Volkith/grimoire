package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.db.AppearancesForEntity
import dev.grimoire.db.Entity
import dev.grimoire.db.Source
import dev.grimoire.db.Template
import dev.grimoire.db.VariantsForEntity
import dev.grimoire.db.Work
import dev.grimoire.db.RelationshipsFor
import dev.grimoire.db.LinksFor
import dev.grimoire.template.Autofill
import dev.grimoire.template.AutofillAction
import dev.grimoire.template.AutofillSource
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkFields
import dev.grimoire.template.TemplateField
import dev.grimoire.template.TemplateFields
import kotlinx.coroutines.flow.combine
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    var type: EntityType = EntityType.CHARACTER; private set
    var name by mutableStateOf(""); private set
    var aliasesText by mutableStateOf(""); private set
    var summary by mutableStateOf(""); private set
    var body by mutableStateOf(""); private set
    var templateId by mutableStateOf<String?>(null); private set
    var attributes by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var dirty by mutableStateOf(false); private set
    /** Field key to the name of the entity autofill copied it from; in-memory only, cleared on the first edit. */
    var autofilledFrom by mutableStateOf<Map<String, String>>(emptyMap()); private set

    // Canon layer (wiki-sourced entities only). The connector owns it; we just display it.
    var canon by mutableStateOf<CanonSnapshot?>(null); private set
    var sourceLabel by mutableStateOf<String?>(null); private set
    var canonStatus by mutableStateOf<String?>(null); private set

    // User-authored entities can be re-filed under any named user source of the world.
    var assignedSourceId by mutableStateOf<String?>(null); private set
    var sourceOptions: Flow<List<Source>> = flowOf(emptyList()); private set

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
        type = EntityType.fromKey(e.type)
        templateOptions = templates.forType(worldId, type)
        workOptions = works.forWorld(worldId)
        val source = repo.sourceById(e.source_id)
        if (source?.kind == "fandom_wiki") {
            canon = CanonSnapshot.decode(e.canon_snapshot)
            sourceLabel = source.label
        }
        if (source?.kind == "user_authored") {
            assignedSourceId = source.id
            sourceOptions = repo.userSources(worldId)
        }
        loaded = true
    }

    /** Files this entity under [sourceId]. Straight-through write, no debounce involvement. */
    fun assignSource(sourceId: String) {
        assignedSourceId = sourceId
        scope.launch { repo.setSource(id, sourceId) }
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
    /** Picking a template also drops its body scaffold into Notes, but only while Notes is blank. */
    fun selectTemplate(id: String?) {
        edit { templateId = id }
        if (id == null || !body.isBlank()) return
        scope.launch {
            val scaffold = templates.template(id).first()?.body_scaffold ?: return@launch
            // Re-check: the user may have typed while the template row was loading.
            if (templateId == id && body.isBlank()) editBody(scaffold)
        }
    }
    /** Attribute values survive template switches; only the visible fields change. */
    fun editAttribute(key: String, v: String) {
        autofilledFrom = autofilledFrom - key
        edit { attributes = attributes + (key to v) }
    }

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
        val next = VariantEditorState(variantId, variantsRepo, attributes, scope)
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

    // --- Link fields: relationships of a given kind/direction/target filter shown inside the template.

    /** Live edges touching this entity with the other end's template, for [linksOf]. */
    val links: Flow<List<LinksFor>> = relationships.links(id)

    fun linksOf(field: TemplateField, all: List<LinksFor>): List<LinksFor> =
        all.filter { LinkFields.matches(field, it) }

    /** Template id to built-in key for the world's live templates, so [LinkFields.passes] can see keys. */
    private fun templateKeys(): Flow<Map<String, String?>> =
        templates.forWorld(worldId).map { list -> list.associate { it.id to it.builtin_key } }

    fun linkTargets(field: TemplateField, text: String): Flow<List<Entity>> {
        val spec = field.link ?: return flowOf(emptyList())
        return combine(searchTargets(text), templateKeys(), links) { found, keys, current ->
            val linked = linksOf(field, current).map { it.other_id }.toSet()
            found.filter { it.id !in linked && LinkFields.passes(spec, it.type, it.template_id, keys[it.template_id]) }
        }
    }

    /**
     * Creates the edge for [field] to [targetId], then fills empty fields whose autofill rules go
     * through it (fill-once, chaining; see [Autofill]).
     */
    fun addLink(field: TemplateField, targetId: String) {
        if (field.link == null) return
        autofilledFrom = autofilledFrom - field.key
        scope.launch {
            if (linksOf(field, relationships.linksNow(id)).any { it.other_id == targetId }) return@launch
            writeLink(field, targetId)
            autofill(field.key)
        }
    }

    /** The one link write path: OUT is this to target, IN is target to this; preset labels unless custom. */
    private suspend fun writeLink(field: TemplateField, targetId: String) {
        val spec = field.link ?: return
        val preset = RelationshipKind.entries.firstOrNull { it.key == spec.kind && it != RelationshipKind.CUSTOM }
        val (from, to) = if (spec.dir == LinkDir.OUT) id to targetId else targetId to id
        relationships.create(worldId, from, to, spec.kind, preset?.label, preset?.inverseLabel)
    }

    private suspend fun fieldsOf(templateId: String?): List<TemplateField> =
        templateId?.let { tid -> templates.template(tid).first()?.let { TemplateFields.decode(it.fields) } }.orEmpty()

    /** One autofill pass at a time, so two quick [addLink]s can't both fill the same empty field. */
    private val autofillLock = Mutex()

    private suspend fun autofill(trigger: String) = autofillLock.withLock {
        val fields = fieldsOf(templateId)
        if (fields.none { f -> f.autofill.any { it.via == trigger } }) return@withLock
        val actions = Autofill.plan(id, fields, attributes, relationships.linksNow(id), trigger) { eid ->
            repo.entity(eid).first()?.let { e ->
                AutofillSource(e.id, e.name, JsonStringMap.decode(e.attributes), fieldsOf(e.template_id), relationships.linksNow(eid))
            }
        }
        if (actions.isEmpty()) return@withLock
        val byKey = fields.associateBy { it.key }
        // Taken before this pass writes anything: a field linked here already was filled by someone else.
        val before = relationships.linksNow(id)
        val recorded = mutableSetOf<String>()
        for (a in actions) {
            val f = byKey[a.fieldKey] ?: continue
            // The user may have typed or linked while the plan was being worked out: never overwrite.
            if (!attributes[a.fieldKey].isNullOrBlank() || before.any { LinkFields.matches(f, it) }) continue
            when (a) {
                is AutofillAction.SetText -> edit { attributes = attributes + (a.fieldKey to a.value) }
                is AutofillAction.AddLink -> writeLink(f, a.targetId)
            }
            // Provenance is the first source that contributed to the field.
            if (recorded.add(a.fieldKey)) autofilledFrom = autofilledFrom + (a.fieldKey to a.fromName)
        }
    }

    /** Soft-deletes a link-field edge; passing [field] drops that field's "from X" label. */
    fun removeLink(relationshipId: String, field: TemplateField? = null) {
        field?.let { autofilledFrom = autofilledFrom - it.key }
        removeRelationship(relationshipId)
    }

    /** Id of the one live entity passing [field]'s filter whose name equals [text] (trimmed, case-insensitive). */
    suspend fun exactMatch(field: TemplateField, text: String): String? {
        val spec = field.link ?: return null
        val wanted = text.trim()
        if (wanted.isEmpty()) return null
        val keys = templates.forWorld(worldId).first().associate { it.id to it.builtin_key }
        val hits = repo.inWorld(worldId).filter {
            it.id != id && it.name.trim().equals(wanted, ignoreCase = true) &&
                LinkFields.passes(spec, it.type, it.template_id, keys[it.template_id])
        }
        return hits.singleOrNull()?.id
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
