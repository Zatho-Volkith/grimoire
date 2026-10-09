package dev.grimoire.template

import dev.grimoire.db.LinksFor

/** Pure filtering rules for LINK template fields. */
object LinkFields {
    /**
     * Whether an entity of [type] with [templateId] (whose built-in key is [builtinKey]) satisfies [spec]'s
     * target filters. A target template matches either the built-in key or the template id; an entity
     * with no template never passes a target-template filter.
     */
    fun passes(spec: LinkSpec, type: String, templateId: String?, builtinKey: String?): Boolean {
        if (spec.targetType != null && spec.targetType != type) return false
        val want = spec.targetTemplate ?: return true
        return (builtinKey != null && builtinKey == want) || (templateId != null && templateId == want)
    }

    /** Whether relationship [row] (seen from the editing entity) belongs under link [field]. */
    fun matches(field: TemplateField, row: LinksFor): Boolean {
        val spec = field.link ?: return false
        return row.kind == spec.kind && row.dir == spec.dir.key &&
            passes(spec, row.other_type, row.other_template_id, row.other_builtin_key)
    }
}
