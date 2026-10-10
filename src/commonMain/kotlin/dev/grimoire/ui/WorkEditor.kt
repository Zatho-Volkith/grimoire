package dev.grimoire.ui

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GCard
import dev.grimoire.ui.design.GChip
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GSelect
import dev.grimoire.ui.design.GTextArea
import dev.grimoire.ui.design.IconSlot
import dev.grimoire.ui.design.ListRow
import dev.grimoire.ui.design.PageHeader
import dev.grimoire.ui.design.SectionLabel
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.grimoire
import dev.grimoire.work.Ao3WorkSkin
import dev.grimoire.work.WorkStatus

/** One work: header with title and status, chapter list, and the open chapter's editor + preview cards. */
@Composable
fun WorkEditor(state: WorkEditorState, onDelete: () -> Unit, onOpenEntity: (String) -> Unit) {
    if (!state.loaded) return
    val chapters by state.chapters.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        PageHeader(
            titleField = {
                GField(
                    state.title, state::editTitle, placeholder = "Title",
                    textStyle = MaterialTheme.typography.headlineMedium, fieldModifier = Modifier.testTag("page-title"), bare = true,
                )
            },
            subtitle = "${chapters.sumOf { it.word_count }} words total",
        ) {
            GSelect(state.status.label, Modifier.width(150.dp)) { close ->
                WorkStatus.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { state.selectStatus(s); close() }) }
            }
            Text(
                if (state.dirty || state.chapterEditor?.dirty == true) "Unsaved" else "Saved",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.grimoire.muted,
            )
            GButton("Delete work", onDelete, kind = ButtonKind.QUIET)
        }
        GTextArea(state.summary, state::editSummary, Modifier.fillMaxWidth(), label = "Summary", minLines = 2)

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            Column(Modifier.width(180.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionLabel("Chapters")
                GButton("New chapter", state::createChapter, Modifier.fillMaxWidth(), kind = ButtonKind.PRIMARY, icon = IconSlot.ADD)
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(chapters, key = { _, c -> c.id }) { index, c ->
                        ListRow(
                            c.title ?: "Chapter ${index + 1}", { state.openChapter(c.id) },
                            secondary = "${c.word_count} words", selected = state.chapterEditor?.id == c.id,
                        )
                    }
                }
            }

            when (val ed = state.chapterEditor) {
                null -> Text("Select or create a chapter.", Modifier.padding(Space.xl), color = MaterialTheme.grimoire.muted)
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
    val cast by ed.cast.collectAsState(initial = emptyList())
    ed.textConvo?.let { TextConvoDialog(it, ed::saveTextConvo, ed::cancelTextConvo) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        GCard(Modifier.weight(1f).fillMaxHeight()) {
            GField(ed.title, ed::editTitle, Modifier.fillMaxWidth(), placeholder = "Chapter title")
            // Writing, export and arrangement actions wrap rather than crowd the title in narrow windows.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                GButton("Text convo", { ed.openTextConvo(cast.map { it.name }) })
                GButton("Copy AO3 HTML", { clipboard.setText(AnnotatedString(ed.ao3Html())) })
                GButton("Copy Work Skin CSS", { clipboard.setText(AnnotatedString(Ao3WorkSkin.css)) })
                GButton(if (ed.showPreview) "Hide preview" else "Preview", ed::togglePreview, kind = ButtonKind.QUIET)
                GButton("Move up", { work.moveOpenChapter(-1) }, kind = ButtonKind.QUIET)
                GButton("Move down", { work.moveOpenChapter(1) }, kind = ButtonKind.QUIET)
                GButton("Delete", work::deleteOpenChapter, kind = ButtonKind.QUIET)
            }
            Text("${ed.wordCount} words · Markdown", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.grimoire.muted)
            CastRow(ed, onOpenEntity)
            GField(
                ed.bodyValue, ed::editBodyValue, Modifier.fillMaxWidth().weight(1f),
                placeholder = "Body", singleLine = false, minLines = 12,
                fieldModifier = Modifier.fillMaxHeight().testTag("chapter-body"),
            )
        }
        if (ed.showPreview) {
            GCard(Modifier.weight(1f).fillMaxHeight(), title = "Preview") {
                MarkdownPreview(ed.body, Modifier.fillMaxSize())
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

    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Cast")
            cast.forEach { m ->
                GChip(
                    m.name, Modifier.testTag("cast-link"),
                    onClick = { onOpenEntity(m.entity_id) }, onRemove = { ed.removeCast(m.entity_id) },
                )
            }
            GField(query, { query = it }, Modifier.width(200.dp), placeholder = "Add to cast")
        }
        if (query.isNotBlank()) {
            candidates.filter { c -> cast.none { it.entity_id == c.id } }.take(6).forEach { e ->
                ListRow(e.name, { ed.addCast(e.id); query = "" }, secondary = dev.grimoire.entity.EntityType.fromKey(e.type).label)
            }
        }
    }
}
