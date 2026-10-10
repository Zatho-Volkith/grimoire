package dev.grimoire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import dev.grimoire.db.Entity
import dev.grimoire.entity.EntityType
import dev.grimoire.relationship.RelationshipKind
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GCard
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GSelect
import dev.grimoire.ui.design.ListRow
import dev.grimoire.ui.design.SectionLabel
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.grimoire

/** Lists both directions of an entity's relationships and adds new outgoing ones, inside one card. */
@Composable
fun RelationshipsPanel(editor: EntityEditorState, onOpenEntity: (String) -> Unit) {
    val rows by editor.relationshipList.collectAsState(initial = emptyList())

    GCard(Modifier.testTag("card-relationships"), title = "Relationships", caption = if (rows.isEmpty()) "None yet." else null) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(if (r.direction == "outgoing") "→" else "←", color = MaterialTheme.grimoire.muted)
                Text(r.shown_label ?: r.kind, style = MaterialTheme.typography.bodyMedium)
                Text(
                    r.other_name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("relationship-link").clickable { onOpenEntity(r.other_id) },
                )
                Text(EntityType.fromKey(r.other_type).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted)
                Box(Modifier.weight(1f))
                GButton("Remove", { editor.removeRelationship(r.id) }, kind = ButtonKind.QUIET)
            }
        }
        AddRelationshipForm(editor)
    }
}

@Composable
private fun AddRelationshipForm(editor: EntityEditorState) {
    var kind by remember { mutableStateOf(RelationshipKind.KNOWS) }
    var label by remember { mutableStateOf(kind.label) }
    var inverse by remember { mutableStateOf(kind.inverseLabel) }
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<Entity?>(null) }
    val candidates by remember(query) { editor.searchTargets(query) }.collectAsState(initial = emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        SectionLabel("Add relationship")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            GSelect(if (kind == RelationshipKind.CUSTOM) "custom" else kind.key, Modifier.weight(1f), label = "Kind") { close ->
                RelationshipKind.entries.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(k.key) },
                        onClick = {
                            kind = k
                            if (k != RelationshipKind.CUSTOM) { label = k.label; inverse = k.inverseLabel }
                            close()
                        },
                    )
                }
            }
            GField(label, { label = it }, Modifier.weight(1f), label = "This side reads")
            GField(inverse, { inverse = it }, Modifier.weight(1f), label = "Other side reads")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            GField(
                query, { query = it; target = null }, Modifier.weight(1f),
                placeholder = target?.let { "Target: ${it.name}" } ?: "Find target entity",
            )
            GButton(
                "Add",
                {
                    editor.addRelationship(target!!.id, kind, label.trim(), inverse.trim())
                    query = ""; target = null
                },
                kind = ButtonKind.PRIMARY,
                enabled = target != null && label.isNotBlank(),
            )
        }
        if (target == null && query.isNotBlank()) {
            candidates.take(8).forEach { e ->
                ListRow(e.name, { target = e; query = e.name }, secondary = EntityType.fromKey(e.type).label)
            }
        }
    }
}
