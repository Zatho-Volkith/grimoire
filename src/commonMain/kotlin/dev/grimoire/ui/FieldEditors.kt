package dev.grimoire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.grimoire.db.LinksFor
import dev.grimoire.template.FieldType
import dev.grimoire.template.TemplateField

/** One template field, rendered by type, followed by its "from X" autofill note when it has one. */
@Composable
fun FieldEditor(state: EntityEditorState, field: TemplateField, links: List<LinksFor>, onOpenEntity: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            field.type == FieldType.LINK && field.link != null -> LinkFieldEditor(state, field, links, onOpenEntity)
            field.type == FieldType.LONG -> OutlinedTextField(
                state.attributes[field.key].orEmpty(), { state.editAttribute(field.key, it) },
                label = { Text(field.label) }, minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            field.options.isNotEmpty() -> OptionsField(state, field)
            else -> OutlinedTextField(
                state.attributes[field.key].orEmpty(), { state.editAttribute(field.key, it) },
                label = { Text(field.label) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AutofillNote(state, field)
    }
}

@Composable
private fun AutofillNote(state: EntityEditorState, field: TemplateField) {
    state.autofilledFrom[field.key]?.let { from ->
        Text(
            "from $from",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.testTag("autofill-note"),
        )
    }
}

/** Single-line text with an editable dropdown of the field's options; any typed value is allowed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsField(state: EntityEditorState, field: TemplateField) {
    var open by remember { mutableStateOf(false) }
    val text = state.attributes[field.key].orEmpty()
    val shown = field.options.filter { it.contains(text.trim(), ignoreCase = true) }

    ExposedDropdownMenuBox(expanded = open && shown.isNotEmpty(), onExpandedChange = { open = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { state.editAttribute(field.key, it); open = true },
            singleLine = true,
            label = { Text(field.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = open && shown.isNotEmpty(), onDismissRequest = { open = false }) {
            shown.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { state.editAttribute(field.key, o); open = false })
            }
        }
    }
}

/**
 * A link field: chips for the live relationships behind it, a "+" that searches the field's target
 * filter, and the attribute text beneath (kept as typed; an exact name match offers a "Link" button).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkFieldEditor(state: EntityEditorState, field: TemplateField, links: List<LinksFor>, onOpenEntity: (String) -> Unit) {
    val mine = state.linksOf(field, links)
    val text = state.attributes[field.key].orEmpty()
    var matchId by remember(field.key) { mutableStateOf<String?>(null) }
    // Re-checked as the text changes and when links change (a just-linked target stops matching).
    LaunchedEffect(text, links) { matchId = state.exactMatch(field, text) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            mine.forEach { l ->
                InputChip(
                    selected = false,
                    onClick = { onOpenEntity(l.other_id) },
                    label = { Text(l.other_name) },
                    trailingIcon = {
                        Text(
                            "✕", color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { state.removeLink(l.id, field) },
                        )
                    },
                    modifier = Modifier.testTag("link-chip"),
                )
            }
            LinkSearchButton(state, field)
        }
        LinkTextRow(text, field, state, matchId?.takeIf { id -> mine.none { it.other_id == id } })
    }
}

@Composable
private fun LinkTextRow(text: String, field: TemplateField, state: EntityEditorState, matchId: String?) {
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            text, { state.editAttribute(field.key, it) },
            label = { Text(field.label) }, singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (matchId != null) TextButton(onClick = { state.addLink(field, matchId) }) { Text("Link") }
    }
}

@Composable
private fun LinkSearchButton(state: EntityEditorState, field: TemplateField) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("link-add-${field.key}")) { Text("+") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false; query = "" }) {
            OutlinedTextField(
                query, { query = it },
                label = { Text("Search ${field.label}") }, singleLine = true,
                modifier = Modifier.width(260.dp).testTag("link-search"),
            )
            val results by remember(query) { state.linkTargets(field, query) }.collectAsState(initial = emptyList())
            results.forEach { e ->
                DropdownMenuItem(
                    text = { Text(e.name) },
                    onClick = { state.addLink(field, e.id); open = false; query = "" },
                )
            }
        }
    }
}
