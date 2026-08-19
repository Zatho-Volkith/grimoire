package dev.grimoire.json

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** JSON `["a","b"]` <-> List<String>, for columns like `entity.aliases`. */
object JsonStrings {
    private val serializer = ListSerializer(String.serializer())

    fun encode(values: List<String>): String = Json.encodeToString(serializer, values)

    fun decode(json: String): List<String> =
        runCatching { Json.decodeFromString(serializer, json) }.getOrDefault(emptyList())
}
