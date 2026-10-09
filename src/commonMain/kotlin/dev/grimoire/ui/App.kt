package dev.grimoire.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import dev.grimoire.ui.design.GrimoireFonts
import dev.grimoire.ui.design.IconLoader
import dev.grimoire.ui.design.LocalIconLoader
import dev.grimoire.ui.design.LocalGrimoireColors
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
                when (val ws = session.workspace) {
                    null -> WorldPickerScreen(session)
                    else -> WorkspaceScreen(session, ws)
                }
            }
        }
    }
}
