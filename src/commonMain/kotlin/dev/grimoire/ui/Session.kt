package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.entity.EntityRepository
import dev.grimoire.export.ExportSink
import dev.grimoire.export.WorldExporter
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.settings.SettingsStore
import dev.grimoire.sync.SyncStatus
import dev.grimoire.template.TemplateRepository
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.tutorial.TutorialWorld
import dev.grimoire.variant.VariantRepository
import dev.grimoire.wiki.WikiConnector
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Top-level app state: which world is open. Owns the repositories the screens need. */
class Session(
    val worlds: WorldRepository,
    val entities: EntityRepository,
    val relationships: RelationshipRepository,
    val works: WorkRepository,
    val timeline: TimelineRepository,
    val templates: TemplateRepository,
    val variants: VariantRepository,
    val wiki: WikiConnector,
    val exporter: WorldExporter,
    val exportSink: ExportSink,
    private val settings: SettingsStore,
    val syncStatus: SyncStatus,
    private val scope: CoroutineScope,
) {
    var workspace by mutableStateOf<WorkspaceState?>(null); private set
    var theme by mutableStateOf(AppTheme.fromKey(settings.load()["theme"])); private set

    fun openWorld(id: String) { workspace = WorkspaceState(id, entities, relationships, works, timeline, templates, variants, wiki, exporter, exportSink, scope) }

    fun closeWorld() {
        val ws = workspace ?: return
        workspace = null
        scope.launch { ws.flush() }
    }

    fun createWorld(name: String) {
        scope.launch { openWorld(worlds.create(name)) }
    }

    /** Seeds and opens the optional guided-tour world. Disposable like any other world. */
    fun createTutorialWorld() {
        scope.launch {
            openWorld(TutorialWorld.create(worlds, entities, relationships, works, timeline, templates, variants))
        }
    }

    fun selectTheme(t: AppTheme) {
        theme = t
        settings.save(settings.load() + ("theme" to t.key))
    }

    /** Fire-and-forget flush — the Ctrl+S path. */
    fun saveNow() { scope.launch { flush() } }

    /** Call before the process exits so a pending debounced save is not lost. */
    suspend fun flush() { workspace?.flush() }
}
