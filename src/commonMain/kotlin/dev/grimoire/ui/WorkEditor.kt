package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.grimoire.work.WorkStatus

/** One work: metadata row, chapter list, and the open chapter's Markdown editor. */
@Composable
fun WorkEditor(state: WorkEditorState, onDelete: () -> Unit) {
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
                else -> ChapterPane(ed, state)
            }
        }
    }
}

@Composable
private fun ChapterPane(ed: ChapterEditorState, work: WorkEditorState) {
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
            TextButton(onClick = { clipboard.setText(AnnotatedString(ed.ao3Html())) }) { Text("Copy AO3 HTML") }
            TextButton(onClick = work::deleteOpenChapter) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        Text("${ed.wordCount} words · Markdown", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        OutlinedTextField(
            ed.body, ed::editBody,
            label = { Text("Body") },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
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
