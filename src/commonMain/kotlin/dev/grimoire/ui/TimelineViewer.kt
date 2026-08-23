package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.grimoire.db.ParticipantNamesForWorld
import dev.grimoire.db.Timeline_event

/**
 * The timeline as a thing to look at: a central spine, cards on whichever side each event
 * chose, era headers over the spine, and sub-events nested inside their parent's card.
 * Every card opens its event; every participant chip opens its entity.
 */
@Composable
fun TimelineViewer(ws: WorkspaceState) {
    val events by ws.eventList.collectAsState(initial = emptyList())
    val participants by ws.eventParticipants.collectAsState(initial = emptyMap())

    val topLevel = events.filter { it.parent_id == null }
    val children = events.filter { it.parent_id != null }.groupBy { it.parent_id!! }

    if (topLevel.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                "No events on this calendar yet.",
                Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        var previousEra: String? = null
        topLevel.forEach { event ->
            val era = event.era
            if (era != null && era != previousEra) {
                // Keyed by the event opening the era run — the same era can recur later.
                item(key = "era-${event.id}") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            era,
                            Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            previousEra = era ?: previousEra

            item(key = event.id) {
                val card: @Composable () -> Unit = {
                    EventCard(event, participants, children[event.id].orEmpty(), ws)
                }
                val date: @Composable (TextAlign) -> Unit = { align ->
                    Text(
                        event.display_date.orEmpty(),
                        Modifier.fillMaxWidth().padding(top = 14.dp, start = 8.dp, end = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = align,
                    )
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.weight(1f)) {
                        if (event.side == "left") card() else date(TextAlign.End)
                    }
                    Spine()
                    Box(Modifier.weight(1f)) {
                        if (event.side == "left") date(TextAlign.Start) else card()
                    }
                }
            }
        }
    }
}

@Composable
private fun Spine() {
    Box(Modifier.width(24.dp).fillMaxHeight()) {
        Box(
            Modifier.width(2.dp).fillMaxHeight()
                .align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Box(
            Modifier.size(10.dp).offset(y = 18.dp)
                .align(Alignment.TopCenter)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun EventCard(
    event: Timeline_event,
    participants: Map<String, List<ParticipantNamesForWorld>>,
    subEvents: List<Timeline_event>,
    ws: WorkspaceState,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
            .testTag("viewer-event")
            .clickable { ws.editEventFromViewer(event.id) },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            event.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
            ParticipantChips(participants[event.id].orEmpty(), ws)
            subEvents.forEach { sub ->
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .testTag("viewer-subevent")
                        .clickable { ws.editEventFromViewer(sub.id) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("↳", color = MaterialTheme.colorScheme.outline)
                        Text(sub.title, style = MaterialTheme.typography.titleSmall)
                        sub.display_date?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    ParticipantChips(participants[sub.id].orEmpty(), ws)
                }
            }
        }
    }
}

@Composable
private fun ParticipantChips(who: List<ParticipantNamesForWorld>, ws: WorkspaceState) {
    if (who.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        who.forEach { p ->
            Text(
                p.name + (p.role?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("viewer-participant").clickable { ws.open(p.entity_id) },
            )
        }
    }
}
