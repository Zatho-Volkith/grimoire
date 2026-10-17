package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GrimoireFonts
import dev.grimoire.ui.design.IconLoader
import dev.grimoire.ui.design.LocalIconLoader
import dev.grimoire.ui.design.LocalGrimoireColors
import dev.grimoire.ui.design.Space
import dev.grimoire.ui.design.grimoireTypography

/** [fonts]/[icons] default to system faces so tests can call `App(session)`; desktop passes the bundled ones. */
@Composable
fun App(
    session: Session,
    fonts: GrimoireFonts = GrimoireFonts.System,
    icons: IconLoader = IconLoader { null },
) {
    val typography = remember(fonts) { grimoireTypography(fonts) }
    MaterialTheme(colorScheme = session.theme.colors, typography = typography) {
        CompositionLocalProvider(LocalGrimoireColors provides session.theme.extra, LocalIconLoader provides icons) {
            Surface(Modifier.fillMaxSize().onPreviewKeyEvent { handleShortcut(session, it) }) {
                Column {
                    session.problems.message?.let { ProblemBanner(it, session.problems::dismiss) }
                    when (val ws = session.workspace) {
                        null -> WorldPickerScreen(session)
                        else -> WorkspaceScreen(session, ws)
                    }
                }
            }
        }
    }
}

/** A background failure, in the error colour, until dismissed. */
@Composable
private fun ProblemBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Space.lg, vertical = Space.xs).testTag("problem-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        GButton("Dismiss", onDismiss, kind = ButtonKind.QUIET)
    }
}
