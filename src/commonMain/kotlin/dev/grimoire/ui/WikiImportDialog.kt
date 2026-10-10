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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.grimoire.entity.EntityType

/** Search a MediaWiki host (fandom.com / wiki.gg) and import a page as an entity. */
@Composable
fun WikiImportDialog(state: WikiImportState, onDismiss: () -> Unit) {
    GDialog(
        "Import from wiki", onDismiss = onDismiss, modifier = Modifier.width(540.dp),
        caption = "Copies the page into this world: the wiki's version becomes canon, your edits stay yours.",
        actions = {
            GButton("Cancel", onDismiss, kind = ButtonKind.QUIET)
            GButton(if (state.busy) "Working…" else "Import", state::importNow, kind = ButtonKind.PRIMARY, enabled = !state.busy && state.selected != null)
        },
    ) {
        GField(state.host, state::editHost, Modifier.fillMaxWidth(), label = "Wiki host (name.fandom.com or name.wiki.gg)")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Bottom) {
            GField(state.query, state::editQuery, Modifier.weight(1f), label = "Page search")
            GButton("Search", state::searchNow, enabled = !state.busy && state.host.isNotBlank())
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.results.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                items(state.results) { title -> ListRow(title, { state.select(title) }, selected = state.selected == title) }
            }
        }
        GSelect(state.type.label, Modifier.width(200.dp), label = "Import as") { close ->
            EntityType.entries.forEach { t -> DropdownMenuItem(text = { Text(t.label) }, onClick = { state.selectType(t); close() }) }
        }
    }
}
