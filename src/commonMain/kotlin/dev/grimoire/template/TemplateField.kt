package dev.grimoire.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

enum class FieldType(val key: String) { TEXT("text"), LONG("long"), LINK("link") }

enum class LinkDir(val key: String) { OUT("out"), IN("in") }

/** Relationship backing a [FieldType.LINK] field: [kind] is a relationship kind key, [dir] which end the entity is. */
data class LinkSpec(
    val kind: String,
    val dir: LinkDir,
    val targetType: String? = null,
    val targetTemplate: String? = null,
)

/** Fill an empty field from the entity reached via relationship kind [via], taking that entity's [from] field. */
data class AutofillRule(val via: String, val from: String)

/**
 * One attribute a template gives its entities. [key] is the stable storage key inside
 * `entity.attributes` — set once when the field is created; [label] is display text and
 * freely renamable without orphaning saved values.
 */
data class TemplateField(
    val key: String,
    val label: String,
    val type: FieldType = FieldType.TEXT,
    val group: String? = null,
    val options: List<String> = emptyList(),
    val link: LinkSpec? = null,
    val autofill: List<AutofillRule> = emptyList(),
)

/** Codec for `template.fields` — a JSON array of field descriptors; members at their default are omitted. */
object TemplateFields {

    fun encode(fields: List<TemplateField>): String = JsonArray(fields.map { f ->
        buildJsonObject {
            put("key", f.key)
            put("label", f.label)
            if (f.type != FieldType.TEXT) put("type", f.type.key)
            f.group?.let { put("group", it) }
            if (f.options.isNotEmpty()) put("options", JsonArray(f.options.map { JsonPrimitive(it) }))
            f.link?.let { l ->
                put("link", buildJsonObject {
                    put("kind", l.kind)
                    put("dir", l.dir.key)
                    l.targetType?.let { put("targetType", it) }
                    l.targetTemplate?.let { put("targetTemplate", it) }
                })
            }
            if (f.autofill.isNotEmpty()) {
                put("autofill", JsonArray(f.autofill.map { r ->
                    buildJsonObject { put("via", r.via); put("from", r.from) }
                }))
            }
        }
    }).toString()

    /** Never throws: non-array input is empty; an element missing `key`/`label` is skipped. */
    fun decode(json: String): List<TemplateField> = runCatching {
        Json.parseToJsonElement(json).jsonArray.mapNotNull { runCatching { decodeField(it.jsonObject) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun decodeField(o: JsonObject): TemplateField {
        val link = (o["link"] as? JsonObject)?.let { l ->
            val kind = l.str("kind") ?: return@let null
            LinkSpec(
                kind,
                LinkDir.entries.firstOrNull { it.key == l.str("dir") } ?: LinkDir.OUT,
                l.str("targetType"),
                l.str("targetTemplate"),
            )
        }
        val type = FieldType.entries.firstOrNull { it.key == o.str("type") } ?: FieldType.TEXT
        return TemplateField(
            key = o.str("key") ?: error("no key"),
            label = o.str("label") ?: error("no label"),
            type = if (type == FieldType.LINK && link == null) FieldType.TEXT else type,
            group = o.str("group"),
            options = (o["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
            link = link,
            autofill = (o["autofill"] as? JsonArray)?.mapNotNull { e ->
                val r = e as? JsonObject ?: return@mapNotNull null
                AutofillRule(r.str("via") ?: return@mapNotNull null, r.str("from") ?: return@mapNotNull null)
            } ?: emptyList(),
        )
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

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
