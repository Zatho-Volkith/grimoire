package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.export.ExportSink
import dev.grimoire.export.WorldExporter
import dev.grimoire.id.IdGenerator
import dev.grimoire.net.Fetcher
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.settings.SettingsStore
import dev.grimoire.template.TemplateRepository
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.variant.VariantRepository
import dev.grimoire.ui.Session
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.io.path.createTempDirectory
import kotlin.random.Random

/** A [Session] over a fresh temp database, for tests that only need top-level state. */
fun testSession(settings: SettingsStore, fetcher: Fetcher = Fetcher { error("no network in tests") }): Session {
    val ids = IdGenerator(Random(1), System::currentTimeMillis)
    val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    return Session(
        WorldRepository(db, ids, System::currentTimeMillis),
        EntityRepository(db, ids, System::currentTimeMillis),
        RelationshipRepository(db, ids, System::currentTimeMillis),
        WorkRepository(db, ids, System::currentTimeMillis),
        TimelineRepository(db, ids, System::currentTimeMillis),
        TemplateRepository(db, ids, System::currentTimeMillis),
        VariantRepository(db, ids, System::currentTimeMillis),
        dev.grimoire.wiki.WikiConnector(db, ids, System::currentTimeMillis, fetcher),
        WorldExporter(db), ExportSink { _, _ -> null }, settings,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
