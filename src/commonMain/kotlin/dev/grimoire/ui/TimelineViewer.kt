package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp

/**
 * The timeline as a thing to look at, not just a list to manage: a vertical spine in reading
 * order, a date gutter, era headers where the era changes, and cards that open their event.
 * Participant chips jump straight to the entity — the "who was there" graph at a glance.
 */
@Composable
fun TimelineViewer(ws: WorkspaceState) {
    val events by ws.eventList.collectAsState(initial = emptyList())
    val participants by ws.eventParticipants.collectAsState(initial = emptyMap())

    if (events.isEmpty()) {
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
        events.forEach { event ->
            val era = event.era
            if (era != null && era != previousEra) {
                item(key = "era-$era") {
                    Text(
                        era,
                        Modifier.padding(start = 140.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            previousEra = era ?: previousEra

            item(key = event.id) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // Date gutter
                    Text(
                        event.display_date.orEmpty(),
                        Modifier.width(116.dp).padding(top = 14.dp, end = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                    // Spine: continuous line with a dot per event
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
                    // Card
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).padding(start = 8.dp, bottom = 12.dp)
                            .testTag("viewer-event")
                            .clickable { ws.editEventFromViewer(event.id) },
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            event.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            }
                            val who = participants[event.id].orEmpty()
                            if (who.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    who.forEach { p ->
                                        Text(
                                            p.name + (p.role?.let { " ($it)" } ?: ""),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.testTag("viewer-participant")
                                                .clickable { ws.open(p.entity_id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
