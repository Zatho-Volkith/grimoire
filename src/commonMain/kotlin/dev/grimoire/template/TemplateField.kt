package dev.grimoire.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * One attribute a template gives its entities. [key] is the stable storage key inside
 * `entity.attributes` — set once when the field is created; [label] is display text and
 * freely renamable without orphaning saved values.
 */
data class TemplateField(val key: String, val label: String)

/** Codec for `template.fields` — a JSON array of `{"key": ..., "label": ...}` descriptors. */
object TemplateFields {

    fun encode(fields: List<TemplateField>): String =
        JsonArray(fields.map { f -> buildJsonObject { put("key", f.key); put("label", f.label) } }).toString()

    fun decode(json: String): List<TemplateField> = runCatching {
        Json.parseToJsonElement(json).jsonArray.map {
            val o = it.jsonObject
            TemplateField(o.getValue("key").jsonPrimitive.content, o.getValue("label").jsonPrimitive.content)
        }
    }.getOrDefault(emptyList())

    /** Slugs [label] into a storage key that doesn't collide with [existing] ones. */
    fun keyFor(label: String, existing: Set<String>): String {
        val base = label.lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
            .replace(Regex("_+"), "_").trim('_')
            .ifEmpty { "field" }
        if (base !in existing) return base
        var n = 2
        while ("${base}_$n" in existing) n++
        return "${base}_$n"
    }
}
