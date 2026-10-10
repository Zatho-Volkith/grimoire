package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.grimoire.entity.EntityType
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GSelect
import dev.grimoire.ui.design.IconSlot
import dev.grimoire.ui.design.ListRow
import dev.grimoire.ui.design.NavRow
import dev.grimoire.ui.design.SectionLabel
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.Widths
import dev.grimoire.ui.design.grimoire
import dev.grimoire.work.WorkStatus

@Composable
fun WorkspaceScreen(session: Session, ws: WorkspaceState) {
    val world by session.worlds.world(ws.worldId).collectAsState(initial = null)

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Sidebar(session, ws, world?.name ?: "")
        when (ws.section) {
            WorkspaceSection.ENTITIES -> EntitiesSection(ws)
            WorkspaceSection.WORKS -> WorksSection(ws)
            WorkspaceSection.TIMELINE -> TimelineSection(ws)
            WorkspaceSection.TEMPLATES -> TemplatesSection(ws)
            WorkspaceSection.TRASH -> TrashSection(ws)
        }
    }
}

private val EntityType.icon: IconSlot
    get() = when (this) {
        EntityType.CHARACTER -> IconSlot.CHARACTER
        EntityType.LOCATION -> IconSlot.LOCATION
        EntityType.FACTION -> IconSlot.FACTION
        EntityType.ITEM -> IconSlot.ITEM
        EntityType.CONCEPT -> IconSlot.CONCEPT
        EntityType.SYSTEM -> IconSlot.SYSTEM
    }

@Composable
private fun Sidebar(session: Session, ws: WorkspaceState, worldName: String) {
    Column(
        Modifier.width(Widths.sidebar).fillMaxHeight().testTag("sidebar")
            .padding(horizontal = Space.md, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(worldName, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        GButton("← Worlds", session::closeWorld, kind = ButtonKind.QUIET)
        Spacer(Modifier.height(Space.md))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NavGroup("World") {
                EntityType.entries.forEach { t ->
                    NavRow(
                        t.plural, t.icon,
                        selected = ws.section == WorkspaceSection.ENTITIES && ws.type == t && ws.search.isBlank(),
                        onClick = { ws.selectType(t) },
                        modifier = Modifier.testTag("nav-" + t.plural.lowercase()),
                    )
                }
            }
            NavGroup("Writing") {
                NavRow("Works", IconSlot.WORKS, ws.section == WorkspaceSection.WORKS, ws::selectWorks, Modifier.testTag("nav-works"))
                NavRow("Timeline", IconSlot.TIMELINE, ws.section == WorkspaceSection.TIMELINE, ws::selectTimeline, Modifier.testTag("nav-timeline"))
            }
            NavGroup("Manage") {
                NavRow("Templates", IconSlot.TEMPLATES, ws.section == WorkspaceSection.TEMPLATES, ws::selectTemplates, Modifier.testTag("nav-templates"))
                NavRow("Trash", IconSlot.TRASH, ws.section == WorkspaceSection.TRASH, ws::selectTrash, Modifier.testTag("nav-trash"))
            }
        }

        ws.exportStatus?.let { StatusLine(it) }
        session.syncStatus.headline?.let { StatusLine(it) }
        ThemeNavRow(session)
        NavRow("Export", IconSlot.EXPORT, selected = false, onClick = { ws.exportWorld(worldName.ifBlank { "World" }) }, modifier = Modifier.testTag("nav-export"))
    }
}

@Composable
private fun NavGroup(label: String, rows: @Composable ColumnScope.() -> Unit) {
    SectionLabel(label, Modifier.padding(start = Space.sm, top = Space.md, bottom = Space.xs))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = rows)
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted,
        modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

/** The narrow list column every section starts with. */
@Composable
private fun ListPane(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.width(Widths.listPane).fillMaxHeight().testTag("list-pane").padding(vertical = Space.lg, horizontal = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

/** The section's main area, a shade apart from the background so panes read without divider lines. */
@Composable
private fun ContentArea(empty: String, content: (@Composable () -> Unit)?) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (content != null) content()
        else Text(
            empty, Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.grimoire.muted,
        )
    }
}

@Composable
private fun EntitiesSection(ws: WorkspaceState) {
    val items by ws.items.collectAsState(initial = emptyList())
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(ws.focusSearchTick) { if (ws.focusSearchTick > 0) searchFocus.requestFocus() }

    ListPane {
        GField(ws.search, ws::searchFor, Modifier.fillMaxWidth().focusRequester(searchFocus), placeholder = "Search all entities")
        GButton("New ${ws.type.label}", ws::createEntity, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, icon = IconSlot.ADD)
        GButton("Import from wiki", ws::openWikiImport, Modifier.fillMaxWidth(), kind = ButtonKind.QUIET)
        SourceFilterRow(ws)
        SectionLabel(if (ws.search.isBlank()) "${items.size} ${ws.type.plural.lowercase()}" else "${items.size} result(s)")
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(items, key = { it.id }) { e ->
                val sub = if (ws.search.isBlank()) e.summary else EntityType.fromKey(e.type).label
                ListRow(e.name, { ws.open(e.id) }, secondary = sub, selected = ws.editor?.id == e.id)
            }
        }
    }
    ws.wikiImport?.let { imp -> WikiImportDialog(imp, onDismiss = ws::closeWikiImport) }
    if (ws.showSources) SourcesDialog(ws)

    ContentArea("Select an entity, or create a new ${ws.type.label.lowercase()}.", ws.editor?.let { ed ->
        { EntityEditor(ed, onDelete = ws::deleteOpenEntity, onOpenEntity = ws::open, onOpenChapter = { w, c -> ws.openWork(w, c) }) }
    })
}

@Composable
private fun WorksSection(ws: WorkspaceState) {
    val works by ws.workList.collectAsState(initial = emptyList())
    val hits by ws.chapterHits.collectAsState(initial = emptyList())

    ListPane {
        GField(ws.workSearch, ws::searchChaptersFor, Modifier.fillMaxWidth(), placeholder = "Search all chapters")
        GButton("New Work", ws::createWork, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, icon = IconSlot.ADD)
        SectionLabel(if (ws.workSearch.isBlank()) "${works.size} work(s)" else "${hits.size} chapter hit(s)")
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (ws.workSearch.isNotBlank()) {
                items(hits, key = { it.id }) { hit ->
                    ListRow(hit.title ?: "Untitled chapter", { ws.openWork(hit.work_id, hit.id) }, secondary = hit.work_title)
                }
            } else {
                items(works, key = { it.id }) { w ->
                    ListRow(w.title, { ws.openWork(w.id) }, secondary = WorkStatus.fromKey(w.status).label, selected = ws.workEditor?.id == w.id)
                }
            }
        }
    }

    ContentArea("Select a work, or create a new one.", ws.workEditor?.let { ed ->
        { WorkEditor(ed, onDelete = ws::deleteOpenWork, onOpenEntity = ws::open) }
    })
}

@Composable
private fun TimelineSection(ws: WorkspaceState) {
    val events by ws.eventList.collectAsState(initial = emptyList())

    ListPane {
        CalendarPicker(ws)
        GButton("New Event", ws::createEvent, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, icon = IconSlot.ADD)
        GButton(if (ws.timelineView) "Back to editing" else "View timeline", ws::toggleTimelineView, Modifier.fillMaxWidth(), kind = ButtonKind.QUIET)
        SectionLabel("${events.size} event(s)")
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(events, key = { it.id }) { e ->
                ListRow(e.title, { ws.openEvent(e.id) }, secondary = e.display_date, selected = ws.eventEditor?.id == e.id)
            }
        }
    }

    val main: (@Composable () -> Unit)? = when {
        ws.timelineView -> { { TimelineViewer(ws) } }
        else -> ws.eventEditor?.let { ed -> { EventEditor(ed, onDelete = ws::deleteOpenEvent, onMove = ws::moveOpenEvent, onOpenEntity = ws::open) } }
    }
    ContentArea("Select an event, or create a new one — or View timeline.", main)
}

@Composable
private fun TemplatesSection(ws: WorkspaceState) {
    val templates by ws.templateList.collectAsState(initial = emptyList())

    ListPane {
        GSelect(ws.templateType.label, Modifier.fillMaxWidth(), label = "For entity type") { close ->
            EntityType.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { ws.selectTemplateType(t); close() })
            }
        }
        GButton("New Template", ws::createTemplate, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, icon = IconSlot.ADD)
        SectionLabel("${templates.size} template(s)")
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(templates, key = { it.id }) { t ->
                ListRow(
                    t.name, { ws.openTemplate(t.id) },
                    secondary = EntityType.fromKey(t.entity_type).label + if (t.is_builtin) " · built-in" else "",
                    selected = ws.templateEditor?.id == t.id,
                )
            }
        }
    }

    ContentArea("Select a template, or create a new one.", ws.templateEditor?.let { ed ->
        { TemplateEditor(ed, onDelete = ws::deleteOpenTemplate) }
    })
}

/** Everything soft-deleted in this world. Restore clears the tombstone; nothing is ever purged. */
@Composable
private fun TrashSection(ws: WorkspaceState) {
    val entities by ws.trashEntities.collectAsState(initial = emptyList())
    val works by ws.trashWorks.collectAsState(initial = emptyList())
    val chapters by ws.trashChapters.collectAsState(initial = emptyList())
    val events by ws.trashEvents.collectAsState(initial = emptyList())
    val templates by ws.trashTemplates.collectAsState(initial = emptyList())
    val empty = entities.isEmpty() && works.isEmpty() && chapters.isEmpty() && events.isEmpty() && templates.isEmpty()

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        item { Text("Trash", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp)) }
        item {
            Text(
                if (empty) "Trash is empty." else "Deleted things wait here until you restore them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        trashGroup("Entities", entities, { it.id }, { it.name }, { EntityType.fromKey(it.type).label }, ws::restoreEntity)
        trashGroup("Works", works, { it.id }, { it.title }, { WorkStatus.fromKey(it.status).label }, ws::restoreWork)
        trashGroup("Chapters", chapters, { it.id }, { it.title ?: "Untitled chapter" }, { "in ${it.work_title}" }, ws::restoreChapter)
        trashGroup("Events", events, { it.id }, { it.title }, { it.display_date ?: "" }, ws::restoreEvent)
        trashGroup("Templates", templates, { it.id }, { it.name }, { EntityType.fromKey(it.entity_type).label }, ws::restoreTemplate)
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.trashGroup(
    title: String,
    items: List<T>,
    key: (T) -> String,
    name: (T) -> String,
    subtitle: (T) -> String,
    restore: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    }
    items(items, key = key) { t ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name(t), style = MaterialTheme.typography.bodyLarge)
            Text(subtitle(t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.weight(1f))
            GButton("Restore", { restore(key(t)) }, kind = ButtonKind.QUIET)
        }
    }
}

@Composable
private fun SourceFilterRow(ws: WorkspaceState) {
    val sources by ws.sourceList.collectAsState(initial = emptyList())
    val label = when (val f = ws.sourceFilter) {
        null -> "All sources"
        SourceFilter.Wiki -> "Wiki imports"
        is SourceFilter.User -> f.label
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GSelect(label, Modifier.weight(1f), label = "Source") { close ->
            DropdownMenuItem(text = { Text("All sources") }, onClick = { ws.filterBySource(null); close() })
            DropdownMenuItem(text = { Text("Wiki imports") }, onClick = { ws.filterBySource(SourceFilter.Wiki); close() })
            sources.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = { ws.filterBySource(SourceFilter.User(s.id, s.label)); close() })
            }
        }
        GButton("Manage", ws::openSources, kind = ButtonKind.QUIET)
    }
}

/** Pick which of the world's calendars the timeline shows; new names create new calendars. */
@Composable
private fun CalendarPicker(ws: WorkspaceState) {
    val known by ws.calendarList.collectAsState(initial = emptyList())
    val calendars = (known + ws.calendar + "default").distinct().sorted()
    var newName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GSelect(ws.calendar, Modifier.fillMaxWidth(), label = "Calendar") { close ->
            calendars.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { ws.selectCalendar(c); close() }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GField(newName, { newName = it }, Modifier.weight(1f), placeholder = "New calendar")
            GButton("Go", { ws.selectCalendar(newName); newName = "" }, kind = ButtonKind.QUIET, enabled = newName.isNotBlank())
        }
    }
}
