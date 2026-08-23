package dev.grimoire.wiki

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The wiki's version of an entity, stored verbatim in `entity.canon_snapshot`. The connector
 * is the ONLY writer; user edits live in the entity's own columns, and "differs from canon"
 * is simply a comparison between the two.
 */
data class CanonSnapshot(
    val name: String,
    val summary: String?,
    val body: String?,
    val attributes: Map<String, String>,
) {
    companion object {
        fun encode(c: CanonSnapshot): String = buildJsonObject {
            put("name", c.name)
            put("summary", c.summary)
            put("body", c.body)
            putJsonObject("attributes") { c.attributes.forEach { (k, v) -> put(k, v) } }
        }.toString()

        fun decode(json: String?): CanonSnapshot? = runCatching {
            val o = Json.parseToJsonElement(json ?: return null).jsonObject
            CanonSnapshot(
                name = o.getValue("name").jsonPrimitive.content,
                summary = o["summary"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                body = o["body"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                attributes = o["attributes"]?.jsonObject.orEmpty()
                    .mapValues { (_, v) -> (v as JsonPrimitive).content },
            )
        }.getOrNull()
    }
}
