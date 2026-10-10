package dev.grimoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GCard
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GTextArea
import dev.grimoire.ui.design.IconSlot
import dev.grimoire.ui.design.PageHeader
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.Widths
import dev.grimoire.ui.design.grimoire

/** One template: name plus its ordered fields. Values live on entities; this edits the shape. */
@Composable
fun TemplateEditor(state: TemplateEditorState, onDelete: () -> Unit) {
    if (!state.loaded) return
    // Which field's detail is open; one at a time keeps the list scannable.
    var expanded by remember(state.id) { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = Widths.editor).fillMaxWidth()
                .padding(Space.xl).testTag("editor-content"),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            PageHeader(
                titleField = {
                    GField(
                        state.name, state::editName, placeholder = "Template name",
                        textStyle = MaterialTheme.typography.headlineMedium,
                        fieldModifier = Modifier.testTag("page-title"), bare = true,
                    )
                },
                subtitle = "${state.entityType.label} template",
            ) {
                Text(if (state.dirty) "Unsaved" else "Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.grimoire.muted)
                GButton("Delete", onDelete, kind = ButtonKind.QUIET)
            }

            GCard(
                Modifier.testTag("card-fields"), title = "Fields",
                caption = if (state.fields.isEmpty()) "No fields yet — add one below." else "Click a field to edit its details.",
            ) {
                state.fields.forEach { f ->
                    TemplateFieldRow(state, f, expanded = expanded == f.key) {
                        expanded = if (expanded == f.key) null else f.key
                    }
                }
                AddFieldForm(state)
            }

            GCard(
                Modifier.testTag("card-scaffold"), title = "Body scaffold",
                caption = "Written to a new entity's Notes when this template is picked (only if Notes is blank).",
            ) {
                GTextArea(
                    state.bodyScaffold, state::editBodyScaffold, Modifier.fillMaxWidth(),
                    placeholder = "Body scaffold", minLines = 4, fieldModifier = Modifier.testTag("body-scaffold"),
                )
            }
        }
    }
}

@Composable
private fun AddFieldForm(state: TemplateEditorState) {
    var label by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        GField(label, { label = it }, Modifier.weight(1f), placeholder = "New field")
        GButton("Add field", { state.addField(label); label = "" }, kind = ButtonKind.PRIMARY, icon = IconSlot.ADD, enabled = label.isNotBlank())
    }
}
