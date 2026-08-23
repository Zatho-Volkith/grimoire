package dev.grimoire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun WorldPickerScreen(session: Session) {
    val worlds by session.worlds.worlds().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Worlds", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            ThemeMenu(session)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, label = { Text("New world") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(
                enabled = name.isNotBlank(),
                onClick = { session.createWorld(name.trim()); name = "" },
            ) { Text("Create") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New here?", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = session::createTutorialWorld) { Text("Create the tutorial world") }
        }
        HorizontalDivider()
        if (worlds.isEmpty()) {
            Text("No worlds yet. Create one above.", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(worlds, key = { it.id }) { w ->
                Column(
                    Modifier.fillMaxWidth().clickable { session.openWorld(w.id) }.padding(vertical = 12.dp, horizontal = 8.dp),
                ) {
                    Text(w.name, style = MaterialTheme.typography.titleMedium)
                    w.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider()
            }
        }
        UpdateRow(session)
    }
}

@Composable
private fun UpdateRow(session: Session) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Grimoire v${session.appVersion}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        when (val state = session.updateState) {
            UpdateState.Idle -> TextButton(onClick = session::checkForUpdates) { Text("Check for updates") }
            UpdateState.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
            UpdateState.UpToDate -> Text("Up to date", style = MaterialTheme.typography.bodySmall)
            UpdateState.Installing -> Text("Downloading installer…", style = MaterialTheme.typography.bodySmall)
            UpdateState.Offline -> {
                Text("Couldn't reach GitHub", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = session::checkForUpdates) { Text("Retry") }
            }
            is UpdateState.Available -> {
                Text("v${state.release.version} available", style = MaterialTheme.typography.bodySmall)
                Button(onClick = session::installUpdate) {
                    Text(if (state.direct) "Install update" else "View release")
                }
            }
        }
    }
}
