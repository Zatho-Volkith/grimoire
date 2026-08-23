package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.grimoire.entity.EntityType
import dev.grimoire.work.WorkStatus
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun WorkspaceScreen(session: Session, ws: WorkspaceState) {
    val world by session.worlds.world(ws.worldId).collectAsState(initial = null)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = session::closeWorld) { Text("← Worlds") }
            Text(world?.name ?: "", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            ws.exportStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            session.syncStatus.headline?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            ThemeMenu(session)
            TextButton(onClick = { ws.exportWorld(world?.name ?: "World") }) { Text("Export") }
        }
        HorizontalDivider()

        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                EntityType.entries.forEach { t ->
                    NavigationRailItem(
                        selected = ws.section == WorkspaceSection.ENTITIES && ws.type == t && ws.search.isBlank(),
                        onClick = { ws.selectType(t) },
                        icon = { Text(t.label.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(t.plural) },
                    )
                }
                NavigationRailItem(
                    selected = ws.section == WorkspaceSection.WORKS,
                    onClick = ws::selectWorks,
                    icon = { Text("W", fontWeight = FontWeight.Bold) },
                    label = { Text("Works") },
                )
                NavigationRailItem(
                    selected = ws.section == WorkspaceSection.TIMELINE,
                    onClick = ws::selectTimeline,
                    icon = { Text("T", fontWeight = FontWeight.Bold) },
                    label = { Text("Timeline") },
                )
                NavigationRailItem(
                    selected = ws.section == WorkspaceSection.TEMPLATES,
                    onClick = ws::selectTemplates,
                    icon = { Text("Tp", fontWeight = FontWeight.Bold) },
                    label = { Text("Templates") },
                )
                NavigationRailItem(
                    selected = ws.section == WorkspaceSection.TRASH,
                    onClick = ws::selectTrash,
                    icon = { Text("🗑", fontWeight = FontWeight.Bold) },
                    label = { Text("Trash") },
                )
            }
            VerticalDivider()

            when (ws.section) {
                WorkspaceSection.ENTITIES -> EntitiesSection(ws)
                WorkspaceSection.WORKS -> WorksSection(ws)
                WorkspaceSection.TIMELINE -> TimelineSection(ws)
                WorkspaceSection.TEMPLATES -> TemplatesSection(ws)
                WorkspaceSection.TRASH -> TrashSection(ws)
            }
        }
    }
}

@Composable
private fun EntitiesSection(ws: WorkspaceState) {
    val items by ws.items.collectAsState(initial = emptyList())
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(ws.focusSearchTick) { if (ws.focusSearchTick > 0) searchFocus.requestFocus() }

    Column(Modifier.width(300.dp).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            ws.search, ws::searchFor,
            label = { Text("Search all entities") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
        )
        Button(onClick = ws::createEntity, modifier = Modifier.fillMaxWidth()) { Text("New ${ws.type.label}") }
        TextButton(onClick = ws::openWikiImport, modifier = Modifier.fillMaxWidth()) { Text("Import from wiki") }
        SourceFilterRow(ws)
        Text(
            if (ws.search.isBlank()) "${items.size} ${ws.type.plural.lowercase()}" else "${items.size} result(s)",
            style = MaterialTheme.typography.labelMedium,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { e ->
                val selected = ws.editor?.id == e.id
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { ws.open(e.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge)
                    val sub = if (ws.search.isBlank()) e.summary else EntityType.fromKey(e.type).label
                    sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
            }
        }
    }
    ws.wikiImport?.let { imp -> WikiImportDialog(imp, onDismiss = ws::closeWikiImport) }
    if (ws.showSources) SourcesDialog(ws)
    VerticalDivider()

    Box(Modifier.fillMaxSize()) {
        when (val ed = ws.editor) {
            null -> Text(
                "Select an entity, or create a new ${ws.type.label.lowercase()}.",
                Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge,
            )
            else -> EntityEditor(ed, onDelete = ws::deleteOpenEntity, onOpenEntity = ws::open, onOpenChapter = { w, c -> ws.openWork(w, c) })
        }
    }
}

@Composable
private fun WorksSection(ws: WorkspaceState) {
    val works by ws.workList.collectAsState(initial = emptyList())

    val hits by ws.chapterHits.collectAsState(initial = emptyList())

    Column(Modifier.width(300.dp).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            ws.workSearch, ws::searchChaptersFor,
            label = { Text("Search all chapters") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ws::createWork, modifier = Modifier.fillMaxWidth()) { Text("New Work") }
        Text(
            if (ws.workSearch.isBlank()) "${works.size} work(s)" else "${hits.size} chapter hit(s)",
            style = MaterialTheme.typography.labelMedium,
        )
        if (ws.workSearch.isNotBlank()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(hits, key = { it.id }) { hit ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { ws.openWork(hit.work_id, hit.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(hit.title ?: "Untitled chapter", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(hit.work_title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(works, key = { it.id }) { w ->
                val selected = ws.workEditor?.id == w.id
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { ws.openWork(w.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(w.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(WorkStatus.fromKey(w.status).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
    VerticalDivider()

    Box(Modifier.fillMaxSize()) {
        when (val ed = ws.workEditor) {
            null -> Text(
                "Select a work, or create a new one.",
                Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge,
            )
            else -> WorkEditor(ed, onDelete = ws::deleteOpenWork, onOpenEntity = ws::open)
        }
    }
}

@Composable
private fun TimelineSection(ws: WorkspaceState) {
    val events by ws.eventList.collectAsState(initial = emptyList())

    Column(Modifier.width(300.dp).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CalendarPicker(ws)
        Button(onClick = ws::createEvent, modifier = Modifier.fillMaxWidth()) { Text("New Event") }
        TextButton(onClick = ws::toggleTimelineView, modifier = Modifier.fillMaxWidth()) {
            Text(if (ws.timelineView) "Back to editing" else "View timeline")
        }
        Text("${events.size} event(s)", style = MaterialTheme.typography.labelMedium)
        LazyColumn(Modifier.fillMaxSize()) {
            items(events, key = { it.id }) { e ->
                val selected = ws.eventEditor?.id == e.id
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { ws.openEvent(e.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    e.display_date?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                }
            }
        }
    }
    VerticalDivider()

    Box(Modifier.fillMaxSize()) {
        when {
            ws.timelineView -> TimelineViewer(ws)
            else -> when (val ed = ws.eventEditor) {
                null -> Text(
                    "Select an event, or create a new one — or View timeline.",
                    Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge,
                )
                else -> EventEditor(ed, onDelete = ws::deleteOpenEvent, onMove = ws::moveOpenEvent, onOpenEntity = ws::open)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatesSection(ws: WorkspaceState) {
    val templates by ws.templateList.collectAsState(initial = emptyList())
    var typeMenuOpen by remember { mutableStateOf(false) }

    Column(Modifier.width(300.dp).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = typeMenuOpen, onExpandedChange = { typeMenuOpen = it }) {
            OutlinedTextField(
                value = ws.templateType.label,
                onValueChange = {}, readOnly = true, singleLine = true,
                label = { Text("For entity type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                EntityType.entries.forEach { t ->
                    DropdownMenuItem(text = { Text(t.label) }, onClick = { ws.selectTemplateType(t); typeMenuOpen = false })
                }
            }
        }
        Button(onClick = ws::createTemplate, modifier = Modifier.fillMaxWidth()) { Text("New Template") }
        Text("${templates.size} template(s)", style = MaterialTheme.typography.labelMedium)
        LazyColumn(Modifier.fillMaxSize()) {
            items(templates, key = { it.id }) { t ->
                val selected = ws.templateEditor?.id == t.id
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { ws.openTemplate(t.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        EntityType.fromKey(t.entity_type).label + if (t.is_builtin) " · built-in" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
    VerticalDivider()

    Box(Modifier.fillMaxSize()) {
        when (val ed = ws.templateEditor) {
            null -> Text(
                "Select a template, or create a new one.",
                Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge,
            )
            else -> TemplateEditor(ed, onDelete = ws::deleteOpenTemplate)
        }
    }
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

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
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
            TextButton(onClick = { restore(key(t)) }) { Text("Restore") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceFilterRow(ws: WorkspaceState) {
    val sources by ws.sourceList.collectAsState(initial = emptyList())
    var open by remember { mutableStateOf(false) }
    val label = when (val f = ws.sourceFilter) {
        null -> "All sources"
        SourceFilter.Wiki -> "Wiki imports"
        is SourceFilter.User -> f.label
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = label, onValueChange = {}, readOnly = true, singleLine = true,
                label = { Text("Source") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("All sources") }, onClick = { ws.filterBySource(null); open = false })
                DropdownMenuItem(text = { Text("Wiki imports") }, onClick = { ws.filterBySource(SourceFilter.Wiki); open = false })
                sources.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = { ws.filterBySource(SourceFilter.User(s.id, s.label)); open = false },
                    )
                }
            }
        }
        TextButton(onClick = ws::openSources) { Text("Manage") }
    }
}

/** Pick which of the world's calendars the timeline shows; new names create new calendars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPicker(ws: WorkspaceState) {
    val known by ws.calendarList.collectAsState(initial = emptyList())
    val calendars = (known + ws.calendar + "default").distinct().sorted()
    var open by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            OutlinedTextField(
                value = ws.calendar, onValueChange = {}, readOnly = true, singleLine = true,
                label = { Text("Calendar") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                calendars.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { ws.selectCalendar(c); open = false })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                newName, { newName = it },
                label = { Text("New calendar") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            TextButton(enabled = newName.isNotBlank(), onClick = { ws.selectCalendar(newName); newName = "" }) { Text("Go") }
        }
    }
}
