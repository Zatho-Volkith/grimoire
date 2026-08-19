package dev.grimoire

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.id.IdGenerator
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.ui.App
import dev.grimoire.ui.Session
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

fun main() {
    val dbFile = DriverFactory.appDataDir().resolve("grimoire.db")
    println("Grimoire database: $dbFile")

    val db = GrimoireDatabase(DriverFactory.create(dbFile))
    val ids = IdGenerator(SecureRandom().asKotlinRandom(), System::currentTimeMillis)
    val clock = System::currentTimeMillis
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val session = Session(WorldRepository(db, ids, clock), EntityRepository(db, ids, clock), RelationshipRepository(db, ids, clock), scope)

    application {
        Window(
            onCloseRequest = {
                runBlocking { session.flush() }
                exitApplication()
            },
            title = "Grimoire",
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
        ) {
            App(session)
        }
    }
}
