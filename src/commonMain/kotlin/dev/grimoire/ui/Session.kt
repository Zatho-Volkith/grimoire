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
import dev.grimoire.update.Release
import dev.grimoire.update.UpdateChecker
import dev.grimoire.update.UpdateLauncher
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
    private val updates: UpdateChecker,
    private val updateLauncher: UpdateLauncher,
    private val scope: CoroutineScope,
) {
    val appVersion: String get() = updates.currentVersion

    var workspace by mutableStateOf<WorkspaceState?>(null); private set
    var theme by mutableStateOf(AppTheme.fromKey(settings.load()["theme"])); private set
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle); private set

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

    fun checkForUpdates() {
        if (updateState == UpdateState.Checking) return
        updateState = UpdateState.Checking
        scope.launch {
            updateState = runCatching { updates.check() }.fold(
                onSuccess = { release ->
                    release?.let { UpdateState.Available(it, direct = updateLauncher.canInstall(it)) }
                        ?: UpdateState.UpToDate
                },
                onFailure = { UpdateState.Offline },
            )
        }
    }

    /** Installs directly where the platform can (Windows MSI), else opens the release page. */
    fun installUpdate() {
        val available = updateState as? UpdateState.Available ?: return
        if (!available.direct) return updateLauncher.openReleasePage(available.release)
        updateState = UpdateState.Installing
        scope.launch {
            flush()
            // On success install() exits the process; reaching onFailure means the download broke.
            runCatching { updateLauncher.install(available.release) }
                .onFailure { updateState = UpdateState.Offline }
        }
    }

    /** Fire-and-forget flush — the Ctrl+S path. */
    fun saveNow() { scope.launch { flush() } }

    /** Call before the process exits so a pending debounced save is not lost. */
    suspend fun flush() { workspace?.flush() }
}

/** Where the manual update check stands. Only ever advanced by user action. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Offline : UpdateState
    data object Installing : UpdateState
    /** [direct] — the platform can download and run the installer itself. */
    data class Available(val release: Release, val direct: Boolean) : UpdateState
}
