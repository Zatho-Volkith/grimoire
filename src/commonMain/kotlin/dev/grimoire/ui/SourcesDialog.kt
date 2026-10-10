package dev.grimoire.ui

import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GDialog
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.GSelect
import dev.grimoire.ui.design.ListRow
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.grimoire
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp

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

    GDialog(
        "Sources", onDismiss = ws::closeSources, modifier = Modifier.width(460.dp),
        caption = "Named layers your entities file under. Wiki pages get their own sources automatically.",
        actions = { GButton("Done", ws::closeSources, kind = ButtonKind.PRIMARY) },
    ) {
        sources.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (editingId == s.id) {
                    GField(editText, { editText = it }, Modifier.weight(1f))
                    GButton("Save", { ws.renameSource(s.id, editText); editingId = null }, kind = ButtonKind.QUIET, enabled = editText.isNotBlank())
                } else {
                    Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    GButton("Rename", { editingId = s.id; editText = s.label }, kind = ButtonKind.QUIET)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            GField(newLabel, { newLabel = it }, Modifier.weight(1f), placeholder = "New source")
            GButton("Add", { ws.createSource(newLabel); newLabel = "" }, enabled = newLabel.isNotBlank())
        }
    }
}
