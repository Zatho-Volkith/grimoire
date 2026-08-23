package dev.grimoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Canon vs yours, field by field. "Take canon" overwrites your side of that one field;
 * keeping yours is simply closing the dialog — nothing is ever taken without a click.
 */
@Composable
fun CanonDiffDialog(state: EntityEditorState) {
    val canon = state.canon ?: return

    data class DiffRow(val field: String, val canonValue: String, val userValue: String, val take: () -> Unit)

    val rows = buildList {
        if (state.name != canon.name) add(DiffRow("Name", canon.name, state.name, state::takeCanonName))
        if (state.summary != canon.summary.orEmpty()) add(DiffRow("Summary", canon.summary.orEmpty(), state.summary, state::takeCanonSummary))
        if (state.body != canon.body.orEmpty()) add(DiffRow("Notes", canon.body.orEmpty(), state.body, state::takeCanonBody))
        canon.attributes.forEach { (key, value) ->
            if (state.attributes[key].orEmpty() != value) {
                add(DiffRow(key, value, state.attributes[key].orEmpty()) { state.takeCanonAttribute(key) })
            }
        }
    }

    Dialog(onDismissRequest = state::closeCanonDiff) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(
                Modifier.width(860.dp).heightIn(max = 620.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Canon vs yours", style = MaterialTheme.typography.titleLarge)
                if (rows.isEmpty()) {
                    Text("In sync with canon — nothing differs.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Field", Modifier.width(120.dp), style = MaterialTheme.typography.labelLarge)
                        Text("Canon says", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text("You say", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text("", Modifier.width(96.dp))
                    }
                    HorizontalDivider()
                    Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                Text(row.field, Modifier.width(120.dp), fontWeight = FontWeight.SemiBold)
                                Text(
                                    row.canonValue.ifEmpty { "—" }, Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 12,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    row.userValue.ifEmpty { "—" }, Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 12,
                                )
                                TextButton(onClick = row.take, modifier = Modifier.width(96.dp)) { Text("Take canon") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = state::closeCanonDiff) { Text("Done — keep the rest as mine") }
                }
            }
        }
    }
}
