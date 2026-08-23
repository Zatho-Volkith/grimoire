package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.grimoire.db.Entity
import dev.grimoire.db.SearchChapters
import dev.grimoire.db.DeletedChaptersForWorld
import dev.grimoire.db.DeletedEntitiesForWorld
import dev.grimoire.db.DeletedEventsForWorld
import dev.grimoire.db.DeletedTemplatesForWorld
import dev.grimoire.db.DeletedWorksForWorld
import dev.grimoire.db.Template
import dev.grimoire.db.Timeline_event
import dev.grimoire.db.Work
import dev.grimoire.entity.EntityRepository
import dev.grimoire.export.ExportSink
import dev.grimoire.export.WorldExporter
import dev.grimoire.entity.EntityType
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.template.TemplateRepository
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.work.WorkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Which kind of thing the workspace is browsing. */
enum class WorkspaceSection { ENTITIES, WORKS, TIMELINE, TEMPLATES, TRASH }

/** One open world: which section and type is browsed, what's searched, what's being edited. */
class WorkspaceState(
    val worldId: String,
    private val entities: EntityRepository,
    private val relationships: RelationshipRepository,
    private val works: WorkRepository,
    private val timeline: TimelineRepository,
    private val templates: TemplateRepository,
    private val exporter: WorldExporter,
    private val exportSink: ExportSink,
    private val scope: CoroutineScope,
) {
    var section by mutableStateOf(WorkspaceSection.ENTITIES); private set
    var type by mutableStateOf(EntityType.CHARACTER); private set
    var search by mutableStateOf(""); private set
    var workSearch by mutableStateOf(""); private set
    var editor by mutableStateOf<EntityEditorState?>(null); private set
    var workEditor by mutableStateOf<WorkEditorState?>(null); private set
    var eventEditor by mutableStateOf<EventEditorState?>(null); private set
    var templateEditor by mutableStateOf<TemplateEditorState?>(null); private set
    /** Which entity type a template created from the Templates section applies to. */
    var templateType by mutableStateOf(EntityType.CHARACTER); private set
    var exportStatus by mutableStateOf<String?>(null); private set
    /** Bumped by [focusSearch]; the screen observes it to move focus into the search field. */
    var focusSearchTick by mutableStateOf(0); private set

    /** The entity list pane: search hits when a query is present, else the selected type. Live. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val items: Flow<List<Entity>> =
        snapshotFlow { type to search }
            .debounce { (_, q) -> if (q.isBlank()) 0L else 150L }
            .flatMapLatest { (t, q) -> if (q.isBlank()) entities.byType(worldId, t) else entities.search(worldId, q) }

    /** The works list pane. Live. */
    val workList: Flow<List<Work>> = works.forWorld(worldId)

    /** Chapter FTS hits for [workSearch]; empty when the query is blank. Live. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val chapterHits: Flow<List<SearchChapters>> =
        snapshotFlow { workSearch }
            .debounce { if (it.isBlank()) 0L else 150L }
            .flatMapLatest { q -> works.searchChapters(worldId, q) }

    /** The timeline pane, in reading order. Live. */
    val eventList: Flow<List<Timeline_event>> = timeline.events(worldId)

    /** All of the world's templates, grouped by entity type. Live. */
    val templateList: Flow<List<Template>> = templates.forWorld(worldId)

    // Trash: everything soft-deleted in this world, newest first. Live.
    val trashEntities: Flow<List<DeletedEntitiesForWorld>> = entities.deleted(worldId)
    val trashWorks: Flow<List<DeletedWorksForWorld>> = works.deletedWorks(worldId)
    val trashChapters: Flow<List<DeletedChaptersForWorld>> = works.deletedChapters(worldId)
    val trashEvents: Flow<List<DeletedEventsForWorld>> = timeline.deleted(worldId)
    val trashTemplates: Flow<List<DeletedTemplatesForWorld>> = templates.deleted(worldId)

    fun selectType(t: EntityType) { section = WorkspaceSection.ENTITIES; type = t; search = "" }
    fun selectWorks() { section = WorkspaceSection.WORKS }
    fun selectTimeline() { section = WorkspaceSection.TIMELINE }
    fun selectTemplates() { section = WorkspaceSection.TEMPLATES }
    fun selectTrash() { section = WorkspaceSection.TRASH }
    fun selectTemplateType(t: EntityType) { templateType = t }
    fun searchFor(q: String) { search = q }
    fun searchChaptersFor(q: String) { workSearch = q }

    fun focusSearch() {
        section = WorkspaceSection.ENTITIES
        focusSearchTick++
    }

    /** Closes whichever editor the active section has open, flushing its pending edits. */
    fun closeCurrent() {
        when (section) {
            WorkspaceSection.ENTITIES -> close()
            WorkspaceSection.WORKS -> { val p = workEditor ?: return; workEditor = null; scope.launch { p.flush() } }
            WorkspaceSection.TIMELINE -> { val p = eventEditor ?: return; eventEditor = null; scope.launch { p.flush() } }
            WorkspaceSection.TEMPLATES -> { val p = templateEditor ?: return; templateEditor = null; scope.launch { p.flush() } }
            WorkspaceSection.TRASH -> Unit
        }
    }

    fun restoreEntity(id: String) { scope.launch { entities.restore(id) } }
    fun restoreWork(id: String) { scope.launch { works.restoreWork(id) } }
    fun restoreChapter(id: String) { scope.launch { works.restoreChapter(id) } }
    fun restoreEvent(id: String) { scope.launch { timeline.restore(id) } }
    fun restoreTemplate(id: String) { scope.launch { templates.restore(id) } }

    fun open(id: String) {
        section = WorkspaceSection.ENTITIES
        if (editor?.id == id) return
        val previous = editor
        val next = EntityEditorState(id, entities, relationships, templates, works, scope)
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

    // --- works ---

    fun openWork(id: String, chapterId: String? = null) {
        section = WorkspaceSection.WORKS
        if (workEditor?.id == id) {
            chapterId?.let { workEditor?.openChapter(it) }
            return
        }
        val previous = workEditor
        val next = WorkEditorState(id, works, entities, scope, chapterId)
        workEditor = next
        scope.launch {
            previous?.flush()
            next.load()
        }
    }

    fun createWork() {
        scope.launch { openWork(works.create(worldId, "Untitled work")) }
    }

    fun deleteOpenWork() {
        val target = workEditor ?: return
        workEditor = null
        scope.launch { works.softDeleteWork(target.id) }
    }

    // --- timeline ---

    fun openEvent(id: String) {
        if (eventEditor?.id == id) return
        val previous = eventEditor
        val next = EventEditorState(id, timeline, entities, scope)
        eventEditor = next
        scope.launch {
            previous?.flush()
            next.load()
        }
    }

    fun createEvent() {
        scope.launch { openEvent(timeline.create(worldId, "New event")) }
    }

    fun deleteOpenEvent() {
        val target = eventEditor ?: return
        eventEditor = null
        scope.launch { timeline.softDelete(target.id) }
    }

    /** Moves the open event one slot up (-1) or down (+1) on the timeline. */
    fun moveOpenEvent(delta: Int) {
        val target = eventEditor ?: return
        scope.launch { timeline.move(target.id, delta) }
    }

    // --- templates ---

    fun openTemplate(id: String) {
        if (templateEditor?.id == id) return
        val previous = templateEditor
        val next = TemplateEditorState(id, templates, scope)
        templateEditor = next
        scope.launch {
            previous?.flush()
            next.load()
        }
    }

    fun createTemplate() {
        scope.launch { openTemplate(templates.create(worldId, templateType, "New ${templateType.label} template")) }
    }

    fun deleteOpenTemplate() {
        val target = templateEditor ?: return
        templateEditor = null
        scope.launch { templates.softDelete(target.id) }
    }

    /** Flushes pending edits, renders the world as a Markdown vault, and hands it to the sink. */
    fun exportWorld(worldName: String) {
        scope.launch {
            flush()
            val files = exporter.export(worldId)
            exportSink.write(worldName, files)?.let { destination ->
                exportStatus = "Exported ${files.size} file(s) to $destination"
            }
        }
    }

    suspend fun flush() {
        editor?.flush()
        workEditor?.flush()
        eventEditor?.flush()
        templateEditor?.flush()
    }
}
