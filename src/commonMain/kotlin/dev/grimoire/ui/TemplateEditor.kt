package dev.grimoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One template: name plus its ordered fields. Values live on entities; this edits the shape. */
@Composable
fun TemplateEditor(state: TemplateEditorState, onDelete: () -> Unit) {
    if (!state.loaded) return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state.name, state::editName,
                label = { Text("Template name") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Text(state.entityType.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            Text(
                if (state.dirty) "Unsaved" else "Saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }

        Text("Fields", style = MaterialTheme.typography.titleMedium)
        if (state.fields.isEmpty()) Text("No fields yet — add one below.", style = MaterialTheme.typography.bodySmall)
        state.fields.forEach { f -> TemplateFieldRow(state, f) }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        AddFieldForm(state)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Body scaffold", style = MaterialTheme.typography.titleMedium)
        Text(
            "Written to a new entity's Notes when this template is picked (only if Notes is blank).",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            state.bodyScaffold, state::editBodyScaffold,
            label = { Text("Body scaffold") }, minLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("body-scaffold"),
        )
    }
}

@Composable
private fun AddFieldForm(state: TemplateEditorState) {
    var label by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            label, { label = it },
            label = { Text("New field") }, singleLine = true,
            modifier = Modifier.width(320.dp),
        )
        Button(enabled = label.isNotBlank(), onClick = { state.addField(label); label = "" }) { Text("Add field") }
    }
}
