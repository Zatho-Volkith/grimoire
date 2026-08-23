package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.grimoire.entity.EntityType

/** Search a MediaWiki host (fandom.com / wiki.gg) and import a page as an entity. */
@Composable
fun WikiImportDialog(state: WikiImportState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(Modifier.width(520.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Import from wiki", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Copies the page into this world: the wiki's version becomes canon, your edits stay yours.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
                OutlinedTextField(
                    state.host, state::editHost,
                    label = { Text("Wiki host (name.fandom.com or name.wiki.gg)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        state.query, state::editQuery,
                        label = { Text("Page search") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(enabled = !state.busy && state.host.isNotBlank(), onClick = state::searchNow) { Text("Search") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (state.results.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(state.results) { title ->
                            Text(
                                title,
                                Modifier.fillMaxWidth()
                                    .background(
                                        if (state.selected == title) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                    )
                                    .clickable { state.select(title) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ImportTypeDropdown(state)
                    Button(enabled = !state.busy && state.selected != null, onClick = state::importNow) {
                        Text(if (state.busy) "Working…" else "Import")
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportTypeDropdown(state: WikiImportState) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.width(180.dp)) {
        OutlinedTextField(
            value = state.type.label,
            onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text("Import as") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EntityType.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { state.selectType(t); open = false })
            }
        }
    }
}
