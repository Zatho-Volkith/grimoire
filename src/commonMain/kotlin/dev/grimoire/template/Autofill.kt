package dev.grimoire.template

import dev.grimoire.db.LinksFor

/** What the planner needs to know about an entity reached through a link field. */
data class AutofillSource(val id: String, val name: String, val attributes: Map<String, String>, val fields: List<TemplateField>, val links: List<LinksFor>)

/** One write the editor should make; [fromName] is the source entity, for the "from X" label. */
sealed interface AutofillAction {
    val fieldKey: String; val fromName: String
    data class SetText(override val fieldKey: String, val value: String, override val fromName: String) : AutofillAction
    data class AddLink(override val fieldKey: String, val targetId: String, override val fromName: String) : AutofillAction
}

/**
 * Fill-once autofill (spec §4): when a link field gains a link, empty fields whose rules go through it
 * take values from the linked entities. Pure planning — the caller applies the actions. Only fields with
 * no matching links and blank text are ever filled; a filled link field chains to its own dependants.
 */
object Autofill {
    private data class Target(val id: String, val name: String, val type: String, val templateId: String?, val builtinKey: String?)

    private fun LinksFor.target() = Target(other_id, other_name, other_type, other_template_id, other_builtin_key)

    suspend fun plan(
        selfId: String, fields: List<TemplateField>, attributes: Map<String, String>, links: List<LinksFor>,
        trigger: String, load: suspend (entityId: String) -> AutofillSource?,
    ): List<AutofillAction> {
        // Simulated state of this entity, updated as actions are planned.
        val linked: Map<String, MutableList<Target>> = fields.filter { it.type == FieldType.LINK && it.link != null }
            .associate { f -> f.key to links.filter { LinkFields.matches(f, it) }.map { it.target() }.toMutableList() }
        val text = attributes.toMutableMap()
        fun isEmpty(f: TemplateField) = linked[f.key].isNullOrEmpty() && text[f.key].isNullOrBlank()

        val cache = mutableMapOf<String, AutofillSource?>()
        suspend fun source(id: String) = if (id in cache) cache[id] else load(id).also { cache[id] = it }

        val actions = mutableListOf<AutofillAction>()
        val queue = ArrayDeque(listOf(trigger))
        val visited = mutableSetOf(trigger)
        while (queue.isNotEmpty()) {
            val v = queue.removeFirst()
            for (f in fields) {
                if (f.autofill.none { it.via == v } || !isEmpty(f)) continue
                val produced = f.autofill.firstNotNullOfOrNull { rule -> produce(selfId, f, rule, linked, ::source) }
                    ?: continue
                actions += produced.map { it.first }
                val set = produced.first().first
                if (set is AutofillAction.SetText) {
                    text[f.key] = set.value
                } else {
                    linked.getValue(f.key) += produced.mapNotNull { it.second }
                    if (visited.add(f.key)) queue.addLast(f.key)
                }
            }
        }
        return actions
    }

    /**
     * Actions [rule] would take for empty field [f] — each AddLink paired with its target so the
     * simulation can grow — or null when the rule yields nothing.
     */
    private suspend fun produce(
        selfId: String, f: TemplateField, rule: AutofillRule,
        linked: Map<String, List<Target>>, source: suspend (String) -> AutofillSource?,
    ): List<Pair<AutofillAction, Target?>>? {
        val adds = mutableListOf<Pair<AutofillAction, Target?>>()
        val seen = mutableSetOf<String>()
        val parts = mutableListOf<String>()
        var textFrom: String? = null
        for (t in linked[rule.via].orEmpty()) {
            val src = source(t.id) ?: continue
            val from = src.fields.firstOrNull { it.key == rule.from } ?: continue
            if (from.type == FieldType.LINK && from.link != null) {
                val values = src.links.filter { LinkFields.matches(from, it) }.map { it.target() }
                if (f.type == FieldType.LINK && f.link != null) {
                    for (v in values) {
                        if (v.id == selfId || !seen.add(v.id)) continue
                        if (!LinkFields.passes(f.link, v.type, v.templateId, v.builtinKey)) continue
                        adds += AutofillAction.AddLink(f.key, v.id, src.name) to v
                    }
                } else if (values.isNotEmpty()) {
                    parts += values.map { it.name }
                    textFrom = textFrom ?: src.name
                }
            } else {
                val value = src.attributes[rule.from]?.takeIf { it.isNotBlank() } ?: continue
                parts += value
                textFrom = textFrom ?: src.name
            }
        }
        if (adds.isNotEmpty()) return adds
        if (parts.isNotEmpty()) return listOf(AutofillAction.SetText(f.key, parts.joinToString(", "), textFrom!!) to null)
        return null
    }
}
