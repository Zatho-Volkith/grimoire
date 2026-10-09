package dev.grimoire.template

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.id.IdGenerator

/**
 * The default field sets every install ships with — global (world_id NULL), fully editable,
 * deletable. Seeding is once-ever: the count includes tombstones, so deleting a built-in is
 * a decision we respect forever, not something the next launch undoes.
 */
object BuiltinTemplates {

    private val defaults: List<Triple<String, String, List<String>>> = listOf(
        Triple("character", "Character sheet", listOf(
            "Age", "Pronouns", "Occupation", "Appearance", "Personality", "Wants", "Fears",
        )),
        Triple("location", "Location sheet", listOf(
            "Kind", "Located in", "Atmosphere", "Notable features",
        )),
        Triple("faction", "Faction sheet", listOf(
            "Purpose", "Leader", "Headquarters", "Allies", "Rivals",
        )),
        Triple("item", "Item sheet", listOf(
            "Kind", "Owner", "Origin", "Properties",
        )),
        Triple("concept", "Concept sheet", listOf(
            "Domain", "Rules and limits", "Source material",
        )),
        Triple("system", "System sheet", listOf(
            "How it works", "Costs and limits", "Practitioners",
        )),
    )

    /** Inserts the defaults if this database has never had built-ins. Safe to call every launch. */
    fun seed(db: GrimoireDatabase, ids: IdGenerator, clock: () -> Long) {
        val q = db.grimoireQueries
        if (q.countBuiltinTemplates().executeAsOne() > 0L) return
        val now = clock()
        db.transaction {
            defaults.forEach { (type, name, labels) ->
                val fields = mutableListOf<TemplateField>()
                labels.forEach { label ->
                    fields += TemplateField(TemplateFields.keyFor(label, fields.map { it.key }.toSet()), label)
                }
                q.insertTemplate(
                    id = ids.next(), world_id = null, entity_type = type, name = name,
                    fields = TemplateFields.encode(fields), is_builtin = true,
                    builtin_key = type, body_scaffold = null,
                    created_at = now, updated_at = now,
                )
            }
        }
    }
}
