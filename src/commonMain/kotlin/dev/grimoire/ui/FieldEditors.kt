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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.grimoire.db.LinksFor
import dev.grimoire.template.FieldType
import dev.grimoire.template.TemplateField
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GChip
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GTextArea
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.grimoire

/** One template field, rendered by type, followed by its "from X" autofill note when it has one. */
@Composable
fun FieldEditor(state: EntityEditorState, field: TemplateField, links: List<LinksFor>, onOpenEntity: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        when {
            field.type == FieldType.LINK && field.link != null -> LinkFieldEditor(state, field, links, onOpenEntity)
            field.type == FieldType.LONG -> GTextArea(
                state.attributes[field.key].orEmpty(), { state.editAttribute(field.key, it) },
                Modifier.fillMaxWidth(), label = field.label,
            )
            field.options.isNotEmpty() -> OptionsField(state, field)
            else -> GField(
                state.attributes[field.key].orEmpty(), { state.editAttribute(field.key, it) },
                Modifier.fillMaxWidth(), label = field.label,
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
            color = MaterialTheme.grimoire.muted,
            modifier = Modifier.testTag("autofill-note"),
        )
    }
}

/** Single-line text with an editable dropdown of the field's options; any typed value is allowed. */
@Composable
private fun OptionsField(state: EntityEditorState, field: TemplateField) {
    var open by remember { mutableStateOf(false) }
    val text = state.attributes[field.key].orEmpty()
    val shown = field.options.filter { it.contains(text.trim(), ignoreCase = true) }

    Box(Modifier.fillMaxWidth()) {
        GField(
            text, { state.editAttribute(field.key, it); open = true }, Modifier.fillMaxWidth(), label = field.label,
            trailing = { Text("▾", color = MaterialTheme.grimoire.muted, modifier = Modifier.clickable { open = !open }) },
        )
        DropdownMenu(expanded = open && shown.isNotEmpty(), onDismissRequest = { open = false }) {
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

    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.grimoire.muted)
            LinkSearchButton(state, field)
        }
        if (mine.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                mine.forEach { l ->
                    GChip(
                        l.other_name, Modifier.testTag("link-chip"),
                        onClick = { onOpenEntity(l.other_id) }, onRemove = { state.removeLink(l.id, field) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            GField(text, { state.editAttribute(field.key, it) }, Modifier.weight(1f), placeholder = field.label)
            val match = matchId?.takeIf { id -> mine.none { it.other_id == id } }
            if (match != null) GButton("Link", { state.addLink(field, match) }, kind = ButtonKind.QUIET)
        }
    }
}

@Composable
private fun LinkSearchButton(state: EntityEditorState, field: TemplateField) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box {
        GButton("+", { open = true }, Modifier.testTag("link-add-${field.key}"), kind = ButtonKind.QUIET)
        DropdownMenu(expanded = open, onDismissRequest = { open = false; query = "" }) {
            GField(query, { query = it }, Modifier.width(260.dp), placeholder = "Search ${field.label}", fieldModifier = Modifier.testTag("link-search"))
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
