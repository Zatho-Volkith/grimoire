package dev.grimoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Manage the world's named user sources — the provenance labels entities file under
 * ("My notes", "Fandom consensus", "Headcanon bible"). Create and rename only: sources
 * with history are never deleted, so provenance can't dangle.
 */
@Composable
fun SourcesDialog(ws: WorkspaceState) {
    val sources by ws.sourceList.collectAsState(initial = emptyList())
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }

    Dialog(onDismissRequest = ws::closeSources) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(Modifier.width(440.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sources", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Named layers your entities file under. Wiki pages get their own sources automatically.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
                sources.forEach { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editingId == s.id) {
                            OutlinedTextField(editText, { editText = it }, singleLine = true, modifier = Modifier.weight(1f))
                            TextButton(
                                enabled = editText.isNotBlank(),
                                onClick = { ws.renameSource(s.id, editText); editingId = null },
                            ) { Text("Save") }
                        } else {
                            Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { editingId = s.id; editText = s.label }) { Text("Rename") }
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newLabel, { newLabel = it },
                        label = { Text("New source") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    Button(enabled = newLabel.isNotBlank(), onClick = { ws.createSource(newLabel); newLabel = "" }) { Text("Add") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ws::closeSources) { Text("Done") }
                }
            }
        }
    }
}
