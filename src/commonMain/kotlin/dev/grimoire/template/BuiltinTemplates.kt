package dev.grimoire.template

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator

/** A template shipped with the app, identified across releases by [key] (`template.builtin_key`). */
data class BuiltinTemplate(
    val key: String,
    val type: EntityType,
    val name: String,
    val fields: List<TemplateField>,
    val scaffold: String?,
)

/**
 * The templates every install ships with — global (world_id NULL), fully editable, deletable.
 * Field keys are spelled out, never slugged from labels, so a label tweak can't change storage;
 * keys of the six original built-ins are frozen (they name saved attributes).
 */
object BuiltinTemplates {

    private fun text(key: String, label: String, group: String? = null, options: List<String> = emptyList()) =
        TemplateField(key, label, group = group, options = options)

    private fun long(key: String, label: String, group: String? = null) =
        TemplateField(key, label, FieldType.LONG, group)

    private fun link(
        key: String, label: String, group: String?,
        kind: String, dir: LinkDir, targetType: String? = null, targetTemplate: String? = null,
        vararg autofill: AutofillRule,
    ) = TemplateField(
        key, label, FieldType.LINK, group,
        link = LinkSpec(kind, dir, targetType, targetTemplate),
        autofill = autofill.toList(),
    )

    private fun scaffold(vararg headings: String): String =
        headings.joinToString("\n\n", postfix = "\n") { "## $it" }

    val all: List<BuiltinTemplate> = listOf(
        BuiltinTemplate("character", EntityType.CHARACTER, "Character sheet", listOf(
            text("age", "Age", "Basics"),
            text("pronouns", "Pronouns", "Basics", listOf("she/her", "he/him", "they/them")),
            text("occupation", "Occupation", "Basics"),
            long("appearance", "Appearance", "Look & manner"),
            long("personality", "Personality", "Look & manner"),
            long("wants", "Wants", "Drive"),
            long("fears", "Fears", "Drive"),
            link("race", "Race", "Ties", "member_of", LinkDir.OUT, "concept", "concept.race"),
            link("faction", "Faction", "Ties", "member_of", LinkDir.OUT, "faction"),
            link("religion", "Religion", "Ties", "follows", LinkDir.OUT, "concept", "concept.religion",
                AutofillRule("race", "religion")),
            link("deity", "Deity", "Ties", "worships", LinkDir.OUT, null, null,
                AutofillRule("race", "deity"), AutofillRule("religion", "deities")),
            link("languages", "Languages", "Ties", "speaks", LinkDir.OUT, "concept", "concept.language",
                AutofillRule("race", "languages")),
            link("homeland", "Homeland", "Ties", "hails_from", LinkDir.OUT, "location", null,
                AutofillRule("race", "homeland")),
        ), scaffold("Backstory", "Arc", "Voice notes")),

        BuiltinTemplate("location", EntityType.LOCATION, "Location sheet", listOf(
            text("kind", "Kind", "Basics", listOf("building", "settlement", "landmark", "wilderness")),
            link("located_in", "Located in", "Basics", "located_in", LinkDir.OUT, "location"),
            long("atmosphere", "Atmosphere", "Feel"),
            long("notable_features", "Notable features", "Feel"),
        ), scaffold("History", "Inhabitants")),

        BuiltinTemplate("location.region", EntityType.LOCATION, "Region", listOf(
            text("climate", "Climate", "Overview"),
            text("terrain", "Terrain", "Overview"),
            link("located_in", "Located in", "Overview", "located_in", LinkDir.OUT, "location"),
            link("contains", "Contains", "Overview", "located_in", LinkDir.IN, "location"),
        ), scaffold("Geography", "History")),

        BuiltinTemplate("location.nation", EntityType.LOCATION, "Nation", listOf(
            text("government", "Government", "Rule",
                listOf("monarchy", "republic", "empire", "theocracy", "council", "tribal")),
            link("ruler", "Ruler", "Rule", "leads", LinkDir.IN),
            text("capital", "Capital", "Rule"),
            text("population", "Population", "Rule"),
            link("located_in", "Located in", "Place", "located_in", LinkDir.OUT, "location"),
            link("allies", "Allies", "Relations", "ally_of", LinkDir.OUT),
            link("rivals", "Rivals", "Relations", "rival_of", LinkDir.OUT),
        ), scaffold("History", "Culture", "Politics")),

        BuiltinTemplate("location.city", EntityType.LOCATION, "City", listOf(
            text("population", "Population", "Basics"),
            link("ruler", "Ruler", "Basics", "leads", LinkDir.IN, null, null,
                AutofillRule("located_in", "ruler")),
            link("located_in", "Located in", "Basics", "located_in", LinkDir.OUT, "location"),
            text("trade", "Trade", "Life"),
            long("notable_features", "Notable features", "Life"),
        ), scaffold("Districts", "History", "Daily life")),

        BuiltinTemplate("faction", EntityType.FACTION, "Faction sheet", listOf(
            long("purpose", "Purpose", "Basics"),
            link("leader", "Leader", "Basics", "leads", LinkDir.IN),
            link("headquarters", "Headquarters", "Basics", "located_in", LinkDir.OUT, "location"),
            link("members", "Members", "People", "member_of", LinkDir.IN),
            link("allies", "Allies", "Relations", "ally_of", LinkDir.OUT),
            link("rivals", "Rivals", "Relations", "rival_of", LinkDir.OUT),
        ), scaffold("History", "Goals", "Structure")),

        BuiltinTemplate("item", EntityType.ITEM, "Item sheet", listOf(
            text("kind", "Kind", "Basics", listOf("weapon", "armor", "tool", "artifact", "document")),
            link("owner", "Owner", "Basics", "owns", LinkDir.IN),
            long("origin", "Origin", "Lore"),
            long("properties", "Properties", "Lore"),
        ), scaffold("History")),

        BuiltinTemplate("item.currency", EntityType.ITEM, "Currency", listOf(
            text("issued_by", "Issued by", "Basics"),
            text("materials", "Materials", "Basics"),
            text("exchange_rate", "Exchange rate", "Basics"),
            long("denominations", "Denominations", "Coins"),
        ), scaffold("History")),

        BuiltinTemplate("concept", EntityType.CONCEPT, "Concept sheet", listOf(
            text("domain", "Domain"),
            long("rules_and_limits", "Rules and limits"),
            text("source_material", "Source material"),
        ), null),

        BuiltinTemplate("concept.race", EntityType.CONCEPT, "Race / people", listOf(
            text("lifespan", "Lifespan", "Basics"),
            link("homeland", "Homeland", "Basics", "located_in", LinkDir.OUT, "location"),
            link("languages", "Languages", "Basics", "speaks", LinkDir.OUT, "concept", "concept.language"),
            link("religion", "Religion", "Faith", "follows", LinkDir.OUT, "concept", "concept.religion"),
            link("deity", "Deity", "Faith", "worships", LinkDir.OUT),
            long("appearance", "Appearance", "Nature"),
            long("culture", "Culture", "Nature"),
        ), scaffold("Origins", "Society", "Subgroups")),

        BuiltinTemplate("concept.species", EntityType.CONCEPT, "Species", listOf(
            text("kind", "Kind", "Basics", listOf("plant", "animal", "other")),
            link("habitat", "Habitat", "Basics", "located_in", LinkDir.OUT, "location"),
            long("appearance", "Appearance", "Nature"),
            long("behavior", "Behavior", "Nature"),
            long("uses", "Uses", "Nature"),
        ), null),

        BuiltinTemplate("concept.religion", EntityType.CONCEPT, "Religion", listOf(
            link("deities", "Deities", "Faith", "worships", LinkDir.OUT),
            long("core_beliefs", "Core beliefs", "Faith"),
            long("hierarchy", "Hierarchy", "Practice"),
            text("holy_sites", "Holy sites", "Practice"),
            link("followers", "Followers", "Practice", "follows", LinkDir.IN),
        ), scaffold("Myths", "Practices", "Holidays")),

        BuiltinTemplate("concept.language", EntityType.CONCEPT, "Language", listOf(
            text("spoken_by", "Spoken by", "Basics"),
            text("script", "Script", "Basics"),
            long("sample_phrases", "Sample phrases", "Usage"),
            long("naming_conventions", "Naming conventions", "Usage"),
        ), scaffold("Grammar notes", "Vocabulary")),

        BuiltinTemplate("concept.holiday", EntityType.CONCEPT, "Holiday", listOf(
            text("date_or_condition", "Date or condition", "When"),
            text("observed_by", "Observed by", "When"),
            long("customs", "Customs", "What"),
            long("origin", "Origin", "What"),
        ), null),

        BuiltinTemplate("system", EntityType.SYSTEM, "System sheet", listOf(
            long("how_it_works", "How it works"),
            long("costs_and_limits", "Costs and limits"),
            text("practitioners", "Practitioners"),
        ), null),

        BuiltinTemplate("system.magic", EntityType.SYSTEM, "Magic", listOf(
            text("source_of_power", "Source of power", "Source"),
            text("who_can_learn", "Who can learn", "Source"),
            long("rules", "Rules", "Rules"),
            long("costs_and_limits", "Costs and limits", "Rules"),
            long("schools", "Schools", "Rules"),
            text("practitioners", "Practitioners", "People"),
        ), scaffold("Schools", "Famous practitioners", "Limits in practice")),

        BuiltinTemplate("system.economy", EntityType.SYSTEM, "Economy", listOf(
            text("currency", "Currency", "Money"),
            text("cost_of_living", "Cost of living", "Money"),
            text("taxation", "Taxation", "Money"),
            long("major_trades", "Major trades", "Trade"),
            long("black_market", "Black market", "Trade"),
        ), null),
    )

    /**
     * Brings this database up to date with [all]; safe to call every launch. Missing built-ins are
     * inserted (a soft-deleted one counts as present, so deletion is respected). A live built-in the
     * user never edited (`updated_at == created_at`) is rewritten in place to the current definition,
     * timestamps untouched so it stays upgradable; an edited one is never touched.
     */
    fun seed(db: GrimoireDatabase, ids: IdGenerator, clock: () -> Long) {
        val q = db.grimoireQueries
        db.transaction {
            val now = clock()
            all.forEach { def ->
                val fields = TemplateFields.encode(def.fields)
                val row = q.templateByBuiltinKey(def.key).executeAsOneOrNull()
                when {
                    row == null -> q.insertTemplate(
                        id = ids.next(), world_id = null, entity_type = def.type.key, name = def.name,
                        fields = fields, is_builtin = true, builtin_key = def.key, body_scaffold = def.scaffold,
                        created_at = now, updated_at = now,
                    )
                    row.deleted_at == null && row.updated_at == row.created_at &&
                        (row.fields != fields || row.body_scaffold != def.scaffold) ->
                        q.upgradeBuiltinTemplate(fields, def.scaffold, row.id)
                }
            }
        }
    }
}
