package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.grimoire.work.Ao3WorkSkin
import dev.grimoire.work.WorkStatus

/** One work: metadata row, chapter list, and the open chapter's Markdown editor. */
@Composable
fun WorkEditor(state: WorkEditorState, onDelete: () -> Unit, onOpenEntity: (String) -> Unit) {
    if (!state.loaded) return
    val chapters by state.chapters.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state.title, state::editTitle,
                label = { Text("Title") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            StatusDropdown(state)
            Text(
                if (state.dirty || state.chapterEditor?.dirty == true) "Unsaved" else "Saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            TextButton(onClick = onDelete) { Text("Delete work", color = MaterialTheme.colorScheme.error) }
        }
        OutlinedTextField(
            state.summary, state::editSummary,
            label = { Text("Summary") }, minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(230.dp).fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chapters", style = MaterialTheme.typography.titleMedium)
                Text("${chapters.sumOf { it.word_count }} words total", style = MaterialTheme.typography.labelMedium)
                Button(onClick = state::createChapter, modifier = Modifier.fillMaxWidth()) { Text("New chapter") }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(chapters, key = { _, c -> c.id }) { index, c ->
                        val selected = state.chapterEditor?.id == c.id
                        Column(
                            Modifier.fillMaxWidth()
                                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable { state.openChapter(c.id) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(c.title ?: "Chapter ${index + 1}", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text("${c.word_count} words", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            VerticalDivider()

            when (val ed = state.chapterEditor) {
                null -> Text(
                    "Select or create a chapter.",
                    Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge,
                )
                else -> ChapterPane(ed, state, onOpenEntity)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterPane(ed: ChapterEditorState, work: WorkEditorState, onOpenEntity: (String) -> Unit) {
    if (!ed.loaded) return
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                ed.title, ed::editTitle,
                label = { Text("Chapter title") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { work.moveOpenChapter(-1) }) { Text("Move up") }
            TextButton(onClick = { work.moveOpenChapter(1) }) { Text("Move down") }
            TextButton(onClick = work::deleteOpenChapter) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        // Writing and export actions wrap rather than crowd the title off-screen.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(ed.ao3Html())) }) { Text("Copy AO3 HTML") }
            TextButton(onClick = { clipboard.setText(AnnotatedString(Ao3WorkSkin.css)) }) { Text("Copy Work Skin CSS") }
            TextButton(onClick = ed::togglePreview) { Text(if (ed.showPreview) "Hide preview" else "Preview") }
        }
        Text("${ed.wordCount} words · Markdown", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        CastRow(ed, onOpenEntity)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                ed.body, ed::editBody,
                label = { Text("Body") },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (ed.showPreview) {
                MarkdownPreview(ed.body, Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(state: WorkEditorState) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.width(170.dp)) {
        OutlinedTextField(
            value = state.status.label,
            onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            WorkStatus.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = { state.selectStatus(s); open = false })
            }
        }
    }
}

/** Who's in this chapter. Members open their entity; typing searches the world to add more. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CastRow(ed: ChapterEditorState, onOpenEntity: (String) -> Unit) {
    val cast by ed.cast.collectAsState(initial = emptyList())
    var query by remember(ed.id) { mutableStateOf("") }
    val candidates by remember(query) { ed.searchCast(query) }.collectAsState(initial = emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Cast:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            cast.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        m.name, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("cast-link").clickable { onOpenEntity(m.entity_id) },
                    )
                    Text(
                        " ✕", color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.clickable { ed.removeCast(m.entity_id) },
                    )
                }
            }
            OutlinedTextField(
                query, { query = it },
                label = { Text("Add to cast") }, singleLine = true,
                modifier = Modifier.width(220.dp),
            )
        }
        if (query.isNotBlank()) {
            candidates.filter { c -> cast.none { it.entity_id == c.id } }.take(6).forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { ed.addCast(e.id); query = "" }.padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(e.name)
                    Text(dev.grimoire.entity.EntityType.fromKey(e.type).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
