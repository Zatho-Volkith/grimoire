package dev.grimoire.json

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** JSON `{"age":"27"}` <-> Map<String, String>, for columns like `entity.attributes`. */
object JsonStringMap {
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(values: Map<String, String>): String = Json.encodeToString(serializer, values)

    fun decode(json: String): Map<String, String> =
        runCatching { Json.decodeFromString(serializer, json) }.getOrDefault(emptyMap())
}
