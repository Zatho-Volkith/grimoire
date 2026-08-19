package dev.grimoire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import dev.grimoire.db.Entity
import dev.grimoire.entity.EntityType
import dev.grimoire.relationship.RelationshipKind

/** Lists both directions of an entity's relationships and adds new outgoing ones. */
@Composable
fun RelationshipsPanel(editor: EntityEditorState, onOpenEntity: (String) -> Unit) {
    val rows by editor.relationshipList.collectAsState(initial = emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Relationships", style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall)
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (r.direction == "outgoing") "→" else "←", color = MaterialTheme.colorScheme.outline)
                Text(r.shown_label ?: r.kind, style = MaterialTheme.typography.bodyMedium)
                Text(
                    r.other_name, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("relationship-link").clickable { onOpenEntity(r.other_id) },
                )
                Text(EntityType.fromKey(r.other_type).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = { editor.removeRelationship(r.id) }) { Text("Remove") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        AddRelationshipForm(editor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRelationshipForm(editor: EntityEditorState) {
    var kind by remember { mutableStateOf(RelationshipKind.KNOWS) }
    var label by remember { mutableStateOf(kind.label) }
    var inverse by remember { mutableStateOf(kind.inverseLabel) }
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Entity?>(null) }
    var kindMenuOpen by remember { mutableStateOf(false) }
    val candidates by remember(query) { editor.searchTargets(query) }.collectAsState(initial = emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Add relationship", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(expanded = kindMenuOpen, onExpandedChange = { kindMenuOpen = it }, modifier = Modifier.width(180.dp)) {
                OutlinedTextField(
                    value = if (kind == RelationshipKind.CUSTOM) "custom" else kind.key,
                    onValueChange = {}, readOnly = true, singleLine = true,
                    label = { Text("Kind") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindMenuOpen) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = kindMenuOpen, onDismissRequest = { kindMenuOpen = false }) {
                    RelationshipKind.entries.forEach { k ->
                        DropdownMenuItem(
                            text = { Text(k.key) },
                            onClick = {
                                kind = k
                                if (k != RelationshipKind.CUSTOM) { label = k.label; inverse = k.inverseLabel }
                                kindMenuOpen = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(label, { label = it }, label = { Text("This side reads") }, singleLine = true, modifier = Modifier.width(180.dp))
            OutlinedTextField(inverse, { inverse = it }, label = { Text("Other side reads") }, singleLine = true, modifier = Modifier.width(180.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it; target = null },
                label = { Text(target?.let { "Target: ${it.name}" } ?: "Find target entity") },
                singleLine = true, modifier = Modifier.width(372.dp),
            )
            Button(
                enabled = target != null && label.isNotBlank(),
                onClick = {
                    editor.addRelationship(target!!.id, kind, label.trim(), inverse.trim())
                    query = ""; target = null
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
