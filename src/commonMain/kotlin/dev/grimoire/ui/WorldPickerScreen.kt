package dev.grimoire.ui

import androidx.compose.foundation.background
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
    var name by remember { mutableStateOf("") }

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

            GCard(
                Modifier.testTag("card-your-worlds"), title = "Your worlds",
                caption = if (worlds.isEmpty()) "No worlds yet. Create one above." else null,
            ) {
                worlds.forEach { w -> ListRow(w.name, { session.openWorld(w.id) }, secondary = w.summary) }
            }

            UpdateRow(session)
        }
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
