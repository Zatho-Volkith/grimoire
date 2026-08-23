package dev.grimoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.foundation.layout.width
import dev.grimoire.template.TemplateFields
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EntityEditor(state: EntityEditorState, onDelete: () -> Unit, onOpenEntity: (String) -> Unit, onOpenChapter: (String, String) -> Unit) {
    if (!state.loaded) return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                state.name, state::editName,
                label = { Text("Name") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(4.dp))
            Text(
                if (state.dirty) "Unsaved" else "Saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        OutlinedTextField(
            state.aliasesText, state::editAliasesText,
            label = { Text("Aliases (comma-separated)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state.summary, state::editSummary,
            label = { Text("Summary") }, minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        AttributesPanel(state)
        OutlinedTextField(
            state.body, state::editBody,
            label = { Text("Notes") }, minLines = 12,
            modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
        )
        RelationshipsPanel(state, onOpenEntity)
        AppearancesPanel(state, onOpenChapter)
    }
}

/** Template picker plus the attribute fields the chosen template declares. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttributesPanel(state: EntityEditorState) {
    val options by state.templateOptions.collectAsState(initial = emptyList())
    val selected = options.firstOrNull { it.id == state.templateId }
    var open by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.width(320.dp)) {
            OutlinedTextField(
                value = selected?.name ?: "None",
                onValueChange = {}, readOnly = true, singleLine = true,
                label = { Text("Template") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { state.selectTemplate(null); open = false })
                options.forEach { t ->
                    DropdownMenuItem(text = { Text(t.name) }, onClick = { state.selectTemplate(t.id); open = false })
                }
            }
        }
        selected?.let { t ->
            val fields = remember(t.fields) { TemplateFields.decode(t.fields) }
            fields.forEach { f ->
                OutlinedTextField(
                    state.attributes[f.key].orEmpty(), { state.editAttribute(f.key, it) },
                    label = { Text(f.label) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Chapters that cast this entity. Managed from the chapter side; rows jump straight there. */
@Composable
private fun AppearancesPanel(state: EntityEditorState, onOpenChapter: (String, String) -> Unit) {
    val rows by state.appearances.collectAsState(initial = emptyList())
    if (rows.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Appears in", style = MaterialTheme.typography.titleMedium)
        rows.forEach { a ->
            Text(
                "${a.work_title} — ${a.chapter_title ?: "Untitled chapter"}",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("appearance-link").clickable { onOpenChapter(a.work_id, a.chapter_id) },
            )
        }
    }
}
