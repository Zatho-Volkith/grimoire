package dev.grimoire

import dev.grimoire.json.JsonStringMap
import dev.grimoire.settings.SettingsStore
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Preferences as a small JSON object next to the database (`settings.json`). */
class FileSettingsStore(private val file: Path) : SettingsStore {

    override fun load(): Map<String, String> =
        runCatching { JsonStringMap.decode(file.readText()) }.getOrDefault(emptyMap())

    override fun save(values: Map<String, String>) {
        file.parent.createDirectories()
        file.writeText(JsonStringMap.encode(values))
    }
}
