package dev.grimoire

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.grimoire.db.GrimoireDatabase
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

object DriverFactory {
    /** Opens (creating and migrating if needed) the SQLite file at [file]. */
    fun create(file: Path = appDataDir().resolve("grimoire.db")): SqlDriver {
        file.parent.createDirectories()
        // foreign_keys is a per-connection pragma, so it must be set on the driver, not in the schema.
        val props = Properties().apply { setProperty("foreign_keys", "true") }
        return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePathString()}", props, GrimoireDatabase.Schema)
    }

    /** Per-OS user data directory for this app. */
    fun appDataDir(appName: String = "grimoire"): Path {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        return when {
            "win" in os -> Path(System.getenv("APPDATA") ?: "$home/AppData/Roaming").resolve(appName)
            "mac" in os -> Path(home, "Library", "Application Support", appName)
            else -> Path(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share").resolve(appName)
        }
    }
}
