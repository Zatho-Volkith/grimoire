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
        val events = q.timelineForWorld(worldId, TimelineRepository.CALENDAR).executeAsList()
        val participants = q.participantsForWorld(worldId).executeAsList().groupBy { it.event_id }
        val works = q.worksForWorld(worldId).executeAsList()
        val chaptersByWork = q.chaptersForWorld(worldId).executeAsList().groupBy { it.work_id }
        val templateFields = q.templatesForWorld(worldId).executeAsList()
            .associate { it.id to TemplateFields.decode(it.fields) }

        val usedNames = mutableSetOf<String>()
        val names = entities.associate { it.id to VaultNames.uniquify(VaultNames.sanitize(it.name), usedNames) }

        buildList {
            add(ExportFile(listOf("About.md"), worldFile(world.name, world.summary)))

            entities.forEach { e ->
                val folder = EntityType.fromKey(e.type).plural
                val fields = e.template_id?.let { templateFields[it] }.orEmpty()
                add(ExportFile(listOf(folder, "${names.getValue(e.id)}.md"), entityFile(e, fields, relationships, names)))
            }

            val usedWorkFolders = mutableSetOf<String>()
            works.forEach { w ->
                val folder = VaultNames.uniquify(VaultNames.sanitize(w.title), usedWorkFolders)
                add(ExportFile(listOf("Works", folder, "About.md"), workFile(w)))
                chaptersByWork[w.id].orEmpty().forEachIndexed { index, c ->
                    add(ExportFile(listOf("Works", folder, chapterFileName(c, index)), chapterFile(c)))
                }
            }

            if (events.isNotEmpty()) {
                add(ExportFile(listOf("Timeline.md"), timelineFile(events, participants, names)))
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
    ): String = buildString {
        appendLine("---")
        appendLine("type: ${EntityType.fromKey(e.type).label}")
        val aliases = JsonStrings.decode(e.aliases)
        if (aliases.isNotEmpty()) {
            appendLine("aliases:")
            aliases.forEach { appendLine("  - ${yamlValue(it)}") }
        }
        val attributes = JsonStringMap.decode(e.attributes)
        val labeled = fields.filter { it.key in attributes }
        labeled.forEach { appendLine("${yamlKey(it.label)}: ${yamlValue(attributes.getValue(it.key))}") }
        (attributes.keys - labeled.map { it.key }.toSet()).sorted()
            .forEach { appendLine("${yamlKey(it)}: ${yamlValue(attributes.getValue(it))}") }
        appendLine("---")

        e.summary?.let { appendLine(); appendLine(it) }
        e.body?.let { appendLine(); appendLine(it) }

        val mine = relationships.filter { it.from_entity == e.id || it.to_entity == e.id }
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
        events: List<Timeline_event>,
        participants: Map<String, List<dev.grimoire.db.ParticipantsForWorld>>,
        names: Map<String, String>,
    ): String = buildString {
        appendLine("# Timeline")
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
