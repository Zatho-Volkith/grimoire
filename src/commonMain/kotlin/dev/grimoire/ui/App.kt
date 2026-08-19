package dev.grimoire.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun App(session: Session) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (val ws = session.workspace) {
                null -> WorldPickerScreen(session)
                else -> WorkspaceScreen(session, ws)
            }
        }
    }
}
