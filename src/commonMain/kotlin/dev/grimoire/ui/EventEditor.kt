package dev.grimoire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import dev.grimoire.db.Entity
import dev.grimoire.entity.EntityType

/** One timeline event: when, what happened, and who was there. */
@Composable
fun EventEditor(state: EventEditorState, onDelete: () -> Unit, onMove: (Int) -> Unit, onOpenEntity: (String) -> Unit) {
    if (!state.loaded) return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                state.title, state::editTitle,
                label = { Text("Title") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (state.dirty) "Unsaved" else "Saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            TextButton(onClick = { onMove(-1) }) { Text("Move up") }
            TextButton(onClick = { onMove(1) }) { Text("Move down") }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state.displayDate, state::editDisplayDate,
                label = { Text("When (free text)") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                state.era, state::editEra,
                label = { Text("Era") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Viewer side:", style = MaterialTheme.typography.labelLarge)
            FilterChip(selected = state.side == "left", onClick = { state.selectSide("left") }, label = { Text("Left") })
            FilterChip(selected = state.side == "right", onClick = { state.selectSide("right") }, label = { Text("Right") })
            ParentPicker(state)
        }
        OutlinedTextField(
            state.description, state::editDescription,
            label = { Text("What happened") }, minLines = 6,
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
        )
        ParticipantsPanel(state, onOpenEntity)
    }
}

@Composable
private fun ParticipantsPanel(editor: EventEditorState, onOpenEntity: (String) -> Unit) {
    val rows by editor.participants.collectAsState(initial = emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Participants", style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) Text("Nobody yet.", style = MaterialTheme.typography.bodySmall)
        rows.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    p.name, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("participant-link").clickable { onOpenEntity(p.entity_id) },
                )
                Text(EntityType.fromKey(p.type).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                p.role?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { editor.removeParticipant(p.entity_id) }) { Text("Remove") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
        Text("Add participant", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it; target = null },
                label = { Text(target?.let { "Participant: ${it.name}" } ?: "Find entity") },
                singleLine = true, modifier = Modifier.width(280.dp),
            )
            OutlinedTextField(role, { role = it }, label = { Text("Role (optional)") }, singleLine = true, modifier = Modifier.width(200.dp))
            Button(
                enabled = target != null,
                onClick = {
                    editor.addParticipant(target!!.id, role.trim())
                    query = ""; role = ""; target = null
                },
            ) { Text("Add") }
        }
        if (target == null && query.isNotBlank()) {
            candidates.take(8).forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { target = e; query = e.name }.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(e.name)
                    Text(EntityType.fromKey(e.type).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** Nest this event under a bigger one (one level deep), or detach it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentPicker(state: EventEditorState) {
    val options by state.parentOptions.collectAsState(initial = emptyList())
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.id == state.parentId }

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.width(240.dp)) {
        OutlinedTextField(
            value = current?.title ?: "Top-level event",
            onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text("Sub-event of") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Top-level event") }, onClick = { state.selectParent(null); open = false })
            options.forEach { e ->
                DropdownMenuItem(text = { Text(e.title) }, onClick = { state.selectParent(e.id); open = false })
            }
        }
    }
}
