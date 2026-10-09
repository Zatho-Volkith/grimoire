package dev.grimoire.export

import dev.grimoire.db.Chapter
import dev.grimoire.db.Entity
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.db.RelationshipsForWorld
import dev.grimoire.db.Timeline_event
import dev.grimoire.db.Work
import dev.grimoire.entity.EntityType
import dev.grimoire.json.JsonStringMap
import dev.grimoire.json.JsonStrings
import dev.grimoire.template.FieldType
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkFields
import dev.grimoire.template.TemplateField
import dev.grimoire.template.TemplateFields
import dev.grimoire.timeline.TimelineRepository
import dev.grimoire.work.WorkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a whole world as an Obsidian-shaped vault of Markdown files:
 *
 * ```
 * About.md                      world name + summary
 * Characters/Kaede.md           one file per entity, grouped by type plural
 * Works/The Long Road/About.md  work metadata
 * Works/The Long Road/01 Departure.md
 * Timeline.md                   events in reading order
 * ```
 *
 * Entity aliases and attributes become YAML frontmatter (`aliases` is native Obsidian);
 * relationships and event participants become `[[wikilinks]]`. Entity file names are unique
 * across the whole vault so every wikilink resolves unambiguously; chapter bodies are already
 * Markdown and export verbatim.
 */
class WorldExporter(private val db: GrimoireDatabase) {

    suspend fun export(worldId: String): List<ExportFile> = withContext(Dispatchers.IO) {
        val q = db.grimoireQueries
        val world = q.worldById(worldId).executeAsOneOrNull() ?: return@withContext emptyList()
        val entities = q.entitiesForWorld(worldId).executeAsList()
        val relationships = q.relationshipsForWorld(worldId).executeAsList()
        val calendars = q.calendarsForWorld(worldId).executeAsList()
        val participants = q.participantsForWorld(worldId).executeAsList().groupBy { it.event_id }
        val works = q.worksForWorld(worldId).executeAsList()
        val chaptersByWork = q.chaptersForWorld(worldId).executeAsList().groupBy { it.work_id }
        val templateRows = q.templatesForWorld(worldId).executeAsList()
        val templateFields = templateRows.associate { it.id to TemplateFields.decode(it.fields) }
        val builtinKeys = templateRows.associate { it.id to it.builtin_key }
        val entitiesById = entities.associateBy { it.id }

        val usedNames = mutableSetOf<String>()
        val names = entities.associate { it.id to VaultNames.uniquify(VaultNames.sanitize(it.name), usedNames) }

        buildList {
            add(ExportFile(listOf("About.md"), worldFile(world.name, world.summary)))

            entities.forEach { e ->
                val folder = EntityType.fromKey(e.type).plural
                val fields = e.template_id?.let { templateFields[it] }.orEmpty()
                add(ExportFile(listOf(folder, "${names.getValue(e.id)}.md"), entityFile(e, fields, relationships, names, entitiesById, builtinKeys)))
            }

            val usedWorkFolders = mutableSetOf<String>()
            works.forEach { w ->
                val folder = VaultNames.uniquify(VaultNames.sanitize(w.title), usedWorkFolders)
                add(ExportFile(listOf("Works", folder, "About.md"), workFile(w)))
                chaptersByWork[w.id].orEmpty().forEachIndexed { index, c ->
                    add(ExportFile(listOf("Works", folder, chapterFileName(c, index)), chapterFile(c)))
                }
            }

            calendars.forEach { calendarKey ->
                val events = q.timelineForWorld(worldId, calendarKey).executeAsList()
                if (events.isNotEmpty()) {
                    val fileName =
                        if (calendarKey == TimelineRepository.CALENDAR) "Timeline.md"
                        else "Timeline - ${VaultNames.sanitize(calendarKey)}.md"
                    add(ExportFile(listOf(fileName), timelineFile(calendarKey, events, participants, names)))
                }
            }
        }
    }

    // --- renderers ---

    private fun worldFile(name: String, summary: String?): String = buildString {
        appendLine("# $name")
        summary?.let { appendLine(); appendLine(it) }
    }

    private fun entityFile(
        e: Entity,
        fields: List<TemplateField>,
        relationships: List<RelationshipsForWorld>,
        names: Map<String, String>,
        entitiesById: Map<String, Entity>,
        builtinKeys: Map<String, String?>,
    ): String = buildString {
        val mine = relationships.filter { it.from_entity == e.id || it.to_entity == e.id }
        appendLine("---")
        appendLine("type: ${EntityType.fromKey(e.type).label}")
        val aliases = JsonStrings.decode(e.aliases)
        if (aliases.isNotEmpty()) {
            appendLine("aliases:")
            aliases.forEach { appendLine("  - ${yamlValue(it)}") }
        }
        val attributes = JsonStringMap.decode(e.attributes)
        // Link fields with matching relationships list [[wikilinks]] (then any free text); the rest are plain.
        val linked = fields.mapNotNull { f ->
            val spec = f.link?.takeIf { f.type == FieldType.LINK } ?: return@mapNotNull null
            val targets = mine.filter { r ->
                val outgoing = r.from_entity == e.id
                val otherId = if (outgoing) r.to_entity else r.from_entity
                val other = entitiesById[otherId] ?: return@filter false
                r.kind == spec.kind && outgoing == (spec.dir == LinkDir.OUT) &&
                    LinkFields.passes(spec, other.type, other.template_id, other.template_id?.let { builtinKeys[it] })
            }.map { names.getValue(if (it.from_entity == e.id) it.to_entity else it.from_entity) }.distinct()
            if (targets.isEmpty()) null else f.key to targets
        }.toMap()
        val labeled = fields.filter { it.key in attributes || it.key in linked }
        labeled.forEach { f ->
            val targets = linked[f.key]
            if (targets == null) {
                appendLine("${yamlKey(f.label)}: ${yamlValue(attributes.getValue(f.key))}")
            } else {
                appendLine("${yamlKey(f.label)}:")
                targets.forEach { appendLine("  - ${yamlValue("[[$it]]")}") }
                attributes[f.key]?.let { appendLine("  - ${yamlValue(it)}") }
            }
        }
        (attributes.keys - labeled.map { it.key }.toSet()).sorted()
            .forEach { appendLine("${yamlKey(it)}: ${yamlValue(attributes.getValue(it))}") }
        appendLine("---")

        e.summary?.let { appendLine(); appendLine(it) }
        e.body?.let { appendLine(); appendLine(it) }

        if (mine.isNotEmpty()) {
            appendLine()
            appendLine("## Relationships")
            appendLine()
            mine.forEach { r ->
                val outgoing = r.from_entity == e.id
                val label = (if (outgoing) r.label else r.inverse_label) ?: r.kind
                val other = names.getValue(if (outgoing) r.to_entity else r.from_entity)
                appendLine("- $label [[$other]]")
            }
        }
    }

    private fun workFile(w: Work): String = buildString {
        appendLine("# ${w.title}")
        appendLine()
        appendLine("Status: ${WorkStatus.fromKey(w.status).label}")
        w.summary?.let { appendLine(); appendLine(it) }
    }

    private fun chapterFileName(c: Chapter, index: Int): String {
        val number = (index + 1).toString().padStart(2, '0')
        val title = c.title?.let { VaultNames.sanitize(it) } ?: "Chapter ${index + 1}"
        return "$number $title.md"
    }

    /** Chapter bodies are already Markdown — exported verbatim (plus a final newline). */
    private fun chapterFile(c: Chapter): String =
        if (c.body.isEmpty() || c.body.endsWith("\n")) c.body else c.body + "\n"

    private fun timelineFile(
        calendarKey: String,
        events: List<Timeline_event>,
        participants: Map<String, List<dev.grimoire.db.ParticipantsForWorld>>,
        names: Map<String, String>,
    ): String = buildString {
        appendLine(if (calendarKey == TimelineRepository.CALENDAR) "# Timeline" else "# Timeline — $calendarKey")
        events.forEach { e ->
            appendLine()
            appendLine("## ${e.title}")
            val date = listOfNotNull(e.display_date, e.era).joinToString(" · ")
            if (date.isNotEmpty()) { appendLine(); appendLine("*$date*") }
            e.description?.let { appendLine(); appendLine(it) }
            val who = participants[e.id].orEmpty()
            if (who.isNotEmpty()) {
                appendLine()
                appendLine("Participants: " + who.joinToString(", ") { p ->
                    "[[${names.getValue(p.entity_id)}]]" + (p.role?.let { " ($it)" } ?: "")
                })
            }
        }
    }

    // --- yaml ---

    private fun yamlValue(v: String): String =
        "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    private fun yamlKey(k: String): String =
        k.replace(Regex("[:#\\n]"), " ").replace(Regex("\\s+"), " ").trim().ifEmpty { "field" }
}
