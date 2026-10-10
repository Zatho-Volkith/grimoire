package dev.grimoire.world

import dev.grimoire.db.World
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Read/merge helpers for `world.settings` (a JSON object; other features own other keys). */
object WorldSettings {
    private const val ARCHIVED = "archived"

    private fun parse(settings: String): JsonObject =
        runCatching { Json.parseToJsonElement(settings).jsonObject }.getOrDefault(JsonObject(emptyMap()))

    fun isArchived(settings: String): Boolean =
        runCatching { parse(settings)[ARCHIVED]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false

    /** Sets or clears the flag, keeping every other key. Unparseable settings are replaced. */
    fun withArchived(settings: String, archived: Boolean): String {
        val rest = parse(settings) - ARCHIVED
        return JsonObject(if (archived) rest + (ARCHIVED to JsonPrimitive(true)) else rest).toString()
    }
}

val World.isArchived: Boolean get() = WorldSettings.isArchived(settings)
