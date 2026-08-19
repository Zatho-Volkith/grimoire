package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.grimoire.entity.EntityType

@Composable
fun WorkspaceScreen(session: Session, ws: WorkspaceState) {
    val world by session.worlds.world(ws.worldId).collectAsState(initial = null)
    val items by ws.items.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = session::closeWorld) { Text("← Worlds") }
            Text(world?.name ?: "", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()

        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                EntityType.entries.forEach { t ->
                    NavigationRailItem(
                        selected = ws.type == t && ws.search.isBlank(),
                        onClick = { ws.selectType(t) },
                        icon = { Text(t.label.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(t.plural) },
                    )
                }
            }
            VerticalDivider()

            Column(Modifier.width(300.dp).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    ws.search, ws::searchFor,
                    label = { Text("Search all entities") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = ws::createEntity, modifier = Modifier.fillMaxWidth()) { Text("New ${ws.type.label}") }
                Text(
                    if (ws.search.isBlank()) "${items.size} ${ws.type.plural.lowercase()}" else "${items.size} result(s)",
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { e ->
                        val selected = ws.editor?.id == e.id
                        Column(
                            Modifier.fillMaxWidth()
                                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable { ws.open(e.id) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(e.name, style = MaterialTheme.typography.bodyLarge)
                            val sub = if (ws.search.isBlank()) e.summary else EntityType.fromKey(e.type).label
                            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                        }
                    }
                }
            }
            VerticalDivider()

            Box(Modifier.fillMaxSize()) {
                when (val ed = ws.editor) {
                    null -> Text(
                        "Select an entity, or create a new ${ws.type.label.lowercase()}.",
                        Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> EntityEditor(ed, onDelete = ws::deleteOpenEntity, onOpenEntity = ws::open)
                }
            }
        }
    }
}
