package dev.grimoire.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.Radius
import dev.grimoire.ui.design.grimoire
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.grimoire.wiki.TextDiff

/**
 * Canon vs yours, changes only: short fields compare side by side; long fields (summary,
 * notes) show just the disagreeing passages — everything unchanged stays hidden. "Take canon"
 * reverts that whole field; keeping yours is simply closing the dialog.
 */
@Composable
fun CanonDiffDialog(state: EntityEditorState) {
    val canon = state.canon ?: return

    data class ShortRow(val field: String, val canonValue: String, val userValue: String, val take: () -> Unit)
    data class LongField(val field: String, val canonValue: String, val userValue: String, val take: () -> Unit)

    val shortRows = buildList {
        if (state.name != canon.name) add(ShortRow("Name", canon.name, state.name, state::takeCanonName))
        canon.attributes.forEach { (key, value) ->
            if (state.attributes[key].orEmpty() != value) {
                add(ShortRow(key, value, state.attributes[key].orEmpty()) { state.takeCanonAttribute(key) })
            }
        }
    }
    val longFields = buildList {
        if (state.summary != canon.summary.orEmpty()) {
            add(LongField("Summary", canon.summary.orEmpty(), state.summary, state::takeCanonSummary))
        }
        if (state.body != canon.body.orEmpty()) {
            add(LongField("Notes", canon.body.orEmpty(), state.body, state::takeCanonBody))
        }
    }

    Dialog(onDismissRequest = state::closeCanonDiff) {
        Surface(color = MaterialTheme.grimoire.card, shape = RoundedCornerShape(Radius.card), border = BorderStroke(1.dp, MaterialTheme.grimoire.border)) {
            Column(
                Modifier.width(760.dp).heightIn(max = 640.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Canon vs yours — changes only", style = MaterialTheme.typography.titleLarge)
                if (shortRows.isEmpty() && longFields.isEmpty()) {
                    Text("In sync with canon — nothing differs.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        shortRows.forEach { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                FieldHeader(row.field, row.take)
                                LabeledLine("Canon", row.canonValue)
                                LabeledLine("Yours", row.userValue)
                            }
                            HorizontalDivider(color = MaterialTheme.grimoire.border)
                        }
                        longFields.forEach { field ->
                            val hunks = remember(field.canonValue, field.userValue) {
                                TextDiff.hunks(field.canonValue, field.userValue)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FieldHeader("${field.field} · ${hunks.size} change(s), unchanged text hidden", field.take)
                                hunks.forEach { hunk ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (hunk.canon.isNotEmpty()) {
                                                LabeledLine("Canon", hunk.canon.joinToString("\n"))
                                            }
                                            if (hunk.yours.isNotEmpty()) {
                                                LabeledLine("Yours", hunk.yours.joinToString("\n"))
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.grimoire.border)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GButton("Done — keep the rest as mine", state::closeCanonDiff, kind = ButtonKind.PRIMARY)
                }
            }
        }
    }
}

@Composable
private fun FieldHeader(title: String, take: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        GButton("Take canon", take, kind = ButtonKind.QUIET)
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            Modifier.width(56.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (label == "Canon") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
        )
        Text(
            value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 14,
            color = if (label == "Canon") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
