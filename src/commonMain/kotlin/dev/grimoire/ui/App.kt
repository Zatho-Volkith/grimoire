package dev.grimoire.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

@Composable
fun App(session: Session) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize().onPreviewKeyEvent { handleShortcut(session, it) }) {
            when (val ws = session.workspace) {
                null -> WorldPickerScreen(session)
                else -> WorkspaceScreen(session, ws)
            }
        }
    }
}
