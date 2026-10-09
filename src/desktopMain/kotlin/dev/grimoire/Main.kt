package dev.grimoire

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.export.WorldExporter
import dev.grimoire.id.IdGenerator
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.sync.SyncService
import dev.grimoire.sync.SyncStatus
import dev.grimoire.template.BuiltinTemplates
import dev.grimoire.template.TemplateRepository
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.update.UpdateChecker
import dev.grimoire.variant.VariantRepository
import dev.grimoire.wiki.WikiConnector
import dev.grimoire.work.WorkRepository
import dev.grimoire.ui.App
import dev.grimoire.ui.Session
import dev.grimoire.ui.handleShortcut
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

/** The running version, baked in as a resource by the generateVersionResource Gradle task. */
private fun appVersion(): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/grimoire-version.txt")) { "version resource missing" }
        .readBytes().decodeToString().trim()

fun main() {
    val dbFile = DriverFactory.appDataDir().resolve("grimoire.db")
    println("Grimoire database: $dbFile")

    val db = GrimoireDatabase(DriverFactory.create(dbFile))
    val ids = IdGenerator(SecureRandom().asKotlinRandom(), System::currentTimeMillis)
    val clock = System::currentTimeMillis
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    BuiltinTemplates.seed(db, ids, clock)
    val fetcher = JdkFetcher()
    val wiki = WikiConnector(db, ids, clock, fetcher)
    val syncStatus = SyncStatus()
    val session = Session(
        WorldRepository(db, ids, clock), EntityRepository(db, ids, clock),
        RelationshipRepository(db, ids, clock), WorkRepository(db, ids, clock),
        TimelineRepository(db, ids, clock), TemplateRepository(db, ids, clock), VariantRepository(db, ids, clock), wiki,
        WorldExporter(db), MarkdownVaultSink(),
        FileSettingsStore(DriverFactory.appDataDir().resolve("settings.json")), syncStatus,
        UpdateChecker(fetcher, appVersion()), DesktopUpdateLauncher(), scope,
    )

    SyncService(db, ids, clock, wiki, status = syncStatus).start(scope)
    val fonts = DesktopFonts.load()

    application {
        Window(
            onCloseRequest = {
                runBlocking { session.flush() }
                exitApplication()
            },
            title = "Grimoire",
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
            onPreviewKeyEvent = { handleShortcut(session, it) },
        ) {
            App(session, fonts)
        }
    }
}
