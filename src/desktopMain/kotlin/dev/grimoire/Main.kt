package dev.grimoire

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.id.IdGenerator
import dev.grimoire.ui.App
import dev.grimoire.world.WorldRepository
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

fun main() {
    val dbFile = DriverFactory.appDataDir().resolve("grimoire.db")
    println("Grimoire database: $dbFile")

    val db = GrimoireDatabase(DriverFactory.create(dbFile))
    val ids = IdGenerator(SecureRandom().asKotlinRandom(), System::currentTimeMillis)
    val worlds = WorldRepository(db, ids, System::currentTimeMillis)

    application {
        Window(onCloseRequest = ::exitApplication, title = "Grimoire") {
            App(worlds)
        }
    }
}
