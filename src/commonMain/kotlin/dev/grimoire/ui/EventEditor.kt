package dev.grimoire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenuItem
import dev.grimoire.db.Entity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GCard
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GSelect
import dev.grimoire.ui.design.GTextArea
import dev.grimoire.ui.design.ListRow
import dev.grimoire.ui.design.PageHeader
import dev.grimoire.ui.design.SectionLabel
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.Widths
import dev.grimoire.ui.design.grimoire
import dev.grimoire.entity.EntityType

/** One timeline event: when, what happened, and who was there. */
@Composable
fun EventEditor(state: EventEditorState, onDelete: () -> Unit, onMove: (Int) -> Unit, onOpenEntity: (String) -> Unit) {
    if (!state.loaded) return

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = Widths.editor).fillMaxWidth().padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            PageHeader(
                titleField = {
                    GField(
                        state.title, state::editTitle, placeholder = "Title",
                        textStyle = MaterialTheme.typography.headlineMedium, fieldModifier = Modifier.testTag("page-title"), bare = true,
                    )
                },
            ) {
                Text(if (state.dirty) "Unsaved" else "Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.grimoire.muted)
                GButton("Move up", { onMove(-1) }, kind = ButtonKind.QUIET)
                GButton("Move down", { onMove(1) }, kind = ButtonKind.QUIET)
                GButton("Delete", onDelete, kind = ButtonKind.QUIET)
            }
            GCard(title = "When") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GField(state.displayDate, state::editDisplayDate, Modifier.weight(1f), label = "When (free text)")
                    GField(state.era, state::editEra, Modifier.weight(1f), label = "Era")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SectionLabel("Viewer side", Modifier.padding(bottom = Space.sm))
                    GButton("Left", { state.selectSide("left") }, kind = if (state.side == "left") ButtonKind.PRIMARY else ButtonKind.SECONDARY)
                    GButton("Right", { state.selectSide("right") }, kind = if (state.side == "right") ButtonKind.PRIMARY else ButtonKind.SECONDARY)
                    ParentPicker(state)
                }
            }
            GCard(title = "What happened") {
                GTextArea(state.description, state::editDescription, Modifier.fillMaxWidth(), placeholder = "What happened", minLines = 6)
            }
            ParticipantsPanel(state, onOpenEntity)
        }
    }
}

@Composable
private fun ParticipantsPanel(editor: EventEditorState, onOpenEntity: (String) -> Unit) {
    val rows by editor.participants.collectAsState(initial = emptyList())

    GCard(title = "Participants", caption = if (rows.isEmpty()) "Nobody yet." else null) {
        rows.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    p.name, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("participant-link").clickable { onOpenEntity(p.entity_id) },
                )
                Text(EntityType.fromKey(p.type).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted)
                p.role?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Box(Modifier.weight(1f))
                GButton("Remove", { editor.removeParticipant(p.entity_id) }, kind = ButtonKind.QUIET)
            }
        }
        AddParticipantForm(editor)
    }
}

@Composable
private fun AddParticipantForm(editor: EventEditorState) {
    var query by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Entity?>(null) }
    val candidates by remember(query) { editor.searchParticipants(query) }.collectAsState(initial = emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("Add participant")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            GField(
                query, { query = it; target = null }, Modifier.weight(1f),
                placeholder = target?.let { "Participant: ${it.name}" } ?: "Find entity",
            )
            GField(role, { role = it }, Modifier.width(200.dp), placeholder = "Role (optional)")
            GButton(
                "Add",
                {
                    editor.addParticipant(target!!.id, role.trim())
                    query = ""; role = ""; target = null
                },
                kind = ButtonKind.PRIMARY, enabled = target != null,
            )
        }
        if (target == null && query.isNotBlank()) {
            candidates.take(8).forEach { e ->
                ListRow(e.name, { target = e; query = e.name }, secondary = EntityType.fromKey(e.type).label)
            }
        }
    }
}

/** Nest this event under a bigger one (one level deep), or detach it. */
@Composable
private fun ParentPicker(state: EventEditorState) {
    val options by state.parentOptions.collectAsState(initial = emptyList())
    val current = options.firstOrNull { it.id == state.parentId }

    GSelect(current?.title ?: "Top-level event", Modifier.width(240.dp), label = "Sub-event of") { close ->
        DropdownMenuItem(text = { Text("Top-level event") }, onClick = { state.selectParent(null); close() })
        options.forEach { e ->
            DropdownMenuItem(text = { Text(e.title) }, onClick = { state.selectParent(e.id); close() })
        }
    }
}
