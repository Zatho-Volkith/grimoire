package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import dev.grimoire.world.isArchived
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GCard
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.ListRow
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.Widths
import dev.grimoire.ui.design.grimoire

/** The opening screen: a centred column with the wordmark, a create card and the world list. */
@Composable
fun WorldPickerScreen(session: Session) {
    val worlds by session.worlds.worlds().collectAsState(initial = emptyList())
    val trashed by session.worlds.deleted().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    // Held here, not in the cards: a card leaves the screen when its list empties and should reopen as it was.
    var archivedOpen by remember { mutableStateOf(false) }
    var trashOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = Widths.picker).fillMaxWidth()
                .padding(Space.xxl).testTag("picker-column"),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Grimoire", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                ThemeMenu(session)
            }

            GCard(Modifier.testTag("card-create-world"), title = "Create world") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    GField(name, { name = it }, Modifier.weight(1f), placeholder = "New world")
                    GButton("Create", { session.createWorld(name.trim()); name = "" }, kind = ButtonKind.PRIMARY, enabled = name.isNotBlank())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text("New here?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted)
                    GButton("Create the tutorial world", session::createTutorialWorld, kind = ButtonKind.QUIET)
                }
            }

            val (archived, active) = worlds.partition { it.isArchived }
            GCard(
                Modifier.testTag("card-your-worlds"), title = "Your worlds",
                caption = if (active.isEmpty()) "No worlds yet. Create one above." else null,
            ) {
                active.forEach { w ->
                    ListRow(w.name, { session.openWorld(w.id) }, secondary = w.summary) {
                        GButton("Archive", { session.archiveWorld(w.id, true) }, Modifier.testTag("world-archive-${w.id}"), kind = ButtonKind.QUIET)
                        GButton("Trash", { session.trashWorld(w.id) }, Modifier.testTag("world-trash-${w.id}"), kind = ButtonKind.DANGER)
                    }
                }
            }

            if (archived.isNotEmpty()) {
                CollapsibleCard("Archived (${archived.size})", "Finished worlds — still here, out of the way.", "card-archived", archivedOpen, { archivedOpen = !archivedOpen }) {
                    archived.forEach { w ->
                        ListRow(w.name, { session.openWorld(w.id) }, secondary = w.summary) {
                            GButton("Unarchive", { session.archiveWorld(w.id, false) }, Modifier.testTag("world-unarchive-${w.id}"), kind = ButtonKind.QUIET)
                            GButton("Trash", { session.trashWorld(w.id) }, Modifier.testTag("world-trash-${w.id}"), kind = ButtonKind.DANGER)
                        }
                    }
                }
            }

            if (trashed.isNotEmpty()) {
                CollapsibleCard("Trash (${trashed.size})", "Trashed worlds keep everything in them until you restore them.", "card-world-trash", trashOpen, { trashOpen = !trashOpen }) {
                    trashed.forEach { w ->
                        ListRow(w.name, {}, secondary = w.summary) {
                            GButton("Restore", { session.restoreWorld(w.id) }, Modifier.testTag("world-restore-${w.id}"), kind = ButtonKind.QUIET)
                        }
                    }
                }
            }

            UpdateRow(session)
        }
    }
}

/** A card whose body opens and closes from its title row. */
@Composable
private fun CollapsibleCard(title: String, caption: String, tag: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    GCard(Modifier.testTag(tag)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Text(if (open) "▾" else "▸", color = MaterialTheme.grimoire.muted, modifier = Modifier.padding(end = Space.sm))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted)
            }
        }
        if (open) content()
    }
}

@Composable
private fun UpdateRow(session: Session) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("Grimoire v${session.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted)
        Spacer(Modifier.weight(1f))
        when (val state = session.updateState) {
            UpdateState.Idle -> GButton("Check for updates", session::checkForUpdates, kind = ButtonKind.QUIET)
            UpdateState.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
            UpdateState.UpToDate -> Text("Up to date", style = MaterialTheme.typography.bodySmall)
            UpdateState.Installing -> Text("Downloading installer…", style = MaterialTheme.typography.bodySmall)
            UpdateState.Offline -> {
                Text("Couldn't reach GitHub", style = MaterialTheme.typography.bodySmall)
                GButton("Retry", session::checkForUpdates, kind = ButtonKind.QUIET)
            }
            is UpdateState.Available -> {
                Text("v${state.release.version} available", style = MaterialTheme.typography.bodySmall)
                GButton(if (state.direct) "Install update" else "View release", session::installUpdate, kind = ButtonKind.PRIMARY)
            }
        }
    }
}
