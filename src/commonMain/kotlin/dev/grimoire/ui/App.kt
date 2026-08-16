package dev.grimoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.launch

/** Wiring smoke test: lists worlds and creates one. Not a real UI. */
@Composable
fun App(repo: WorldRepository) {
    val worlds by repo.worlds().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, label = { Text("World name") }, singleLine = true)
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val n = name.trim()
                            name = ""
                            scope.launch { repo.create(n) }
                        },
                    ) { Text("Create world") }
                }
                Text("${worlds.size} world(s)", style = MaterialTheme.typography.labelMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(worlds, key = { it.id }) { w -> Text("${w.name}  —  ${w.id}") }
                }
            }
        }
    }
}
