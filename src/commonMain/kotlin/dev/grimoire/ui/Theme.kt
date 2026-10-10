package dev.grimoire.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.grimoire.ui.design.GrimoireColors
import dev.grimoire.ui.design.IconSlot
import dev.grimoire.ui.design.NavRow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Curated color themes. [key] is what `settings.json` stores; the schemes are full Material3
 * palettes so every surface, selection highlight, and divider follows the choice.
 */
enum class AppTheme(
    val key: String,
    val label: String,
    val colors: ColorScheme,
    val extra: GrimoireColors = GrimoireColors.derive(colors),
) {

    /** Default: dark ink-violet with antique gold — the tome itself. */
    GRIMOIRE(
        "grimoire", "Grimoire",
        darkColorScheme(
            primary = Color(0xFFD9B96C), onPrimary = Color(0xFF241A05),
            primaryContainer = Color(0xFF4A3A14), onPrimaryContainer = Color(0xFFF5E3B3),
            secondary = Color(0xFFA98BD6), onSecondary = Color(0xFF1E1233),
            secondaryContainer = Color(0xFF3A2B57), onSecondaryContainer = Color(0xFFE9DCFF),
            tertiary = Color(0xFF87B8A0),
            background = Color(0xFF151020), onBackground = Color(0xFFE8E0F0),
            surface = Color(0xFF1B1526), onSurface = Color(0xFFE8E0F0),
            surfaceVariant = Color(0xFF272033), onSurfaceVariant = Color(0xFFCBBFDD),
            outline = Color(0xFF7A6C93), outlineVariant = Color(0xFF3A3149),
            error = Color(0xFFE58A85),
        ),
    ),

    /** Warm cream and leather-brown ink — writing on old paper. */
    PARCHMENT(
        "parchment", "Parchment",
        lightColorScheme(
            primary = Color(0xFF6E4522), onPrimary = Color(0xFFFFF6E5),
            primaryContainer = Color(0xFFE9D3AE), onPrimaryContainer = Color(0xFF2E1B08),
            secondary = Color(0xFF7C2F32), onSecondary = Color(0xFFFFF1EE),
            secondaryContainer = Color(0xFFE8CFB0), onSecondaryContainer = Color(0xFF33200B),
            tertiary = Color(0xFF4E6151),
            background = Color(0xFFF3E9CF), onBackground = Color(0xFF33281A),
            surface = Color(0xFFFAF2DD), onSurface = Color(0xFF33281A),
            surfaceVariant = Color(0xFFEADFC2), onSurfaceVariant = Color(0xFF5C4F38),
            outline = Color(0xFF8A7A5C), outlineVariant = Color(0xFFD4C6A4),
            error = Color(0xFF9A342E),
        ),
    ),

    /** Blue-black with steel and sea-glass accents. */
    MIDNIGHT(
        "midnight", "Midnight",
        darkColorScheme(
            primary = Color(0xFF9DC1E4), onPrimary = Color(0xFF0B1B2B),
            primaryContainer = Color(0xFF23425F), onPrimaryContainer = Color(0xFFD6E7F7),
            secondary = Color(0xFF77C9B4), onSecondary = Color(0xFF072019),
            secondaryContainer = Color(0xFF1E3A4F), onSecondaryContainer = Color(0xFFDDF0FF),
            tertiary = Color(0xFFB0A7E0),
            background = Color(0xFF0C1220), onBackground = Color(0xFFDEE6F2),
            surface = Color(0xFF121A2B), onSurface = Color(0xFFDEE6F2),
            surfaceVariant = Color(0xFF1D2940), onSurfaceVariant = Color(0xFFBAC7DC),
            outline = Color(0xFF6E7FA0), outlineVariant = Color(0xFF2B3A55),
            error = Color(0xFFE58A85),
        ),
    ),

    /** Deep green with amber, in Ink & Thread's spirit — a field guide by lamplight. */
    FOREST(
        "forest", "Forest",
        darkColorScheme(
            primary = Color(0xFFE8963F), onPrimary = Color(0xFF1A1208),
            primaryContainer = Color(0xFF4A3214), onPrimaryContainer = Color(0xFFF7DDBA),
            secondary = Color(0xFF8FB59A), onSecondary = Color(0xFF0F2016),
            secondaryContainer = Color(0xFF2E4636), onSecondaryContainer = Color(0xFFDCEBDF),
            tertiary = Color(0xFFE3B95F),
            background = Color(0xFF1B2F22), onBackground = Color(0xFFEDE6D3),
            surface = Color(0xFF203626), onSurface = Color(0xFFEDE6D3),
            surfaceVariant = Color(0xFF2E4636), onSurfaceVariant = Color(0xFFCFC6AE),
            outline = Color(0xFF5E7A66), outlineVariant = Color(0xFF34503E),
            error = Color(0xFFE58A85),
        ),
        GrimoireColors(
            card = Color(0xFF22392A), field = Color(0xFF2E4636), border = Color(0xFF34503E),
            muted = Color(0xFFE3B95F), selected = Color(0xFFE8963F).copy(alpha = 0.16f),
        ),
    ),

    /** Plain light, for daylight and pragmatists. */
    IVORY("ivory", "Ivory", lightColorScheme());

    companion object {
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: GRIMOIRE
    }
}

/** Sidebar theme picker: a nav row that opens the palette list. */
@Composable
fun ThemeNavRow(session: Session) {
    var open by remember { mutableStateOf(false) }
    Box {
        NavRow(
            "Theme: ${session.theme.label}", icon = IconSlot.SETTINGS, selected = false, onClick = { open = true },
            modifier = Modifier.testTag("nav-theme"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppTheme.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { session.selectTheme(t); open = false })
            }
        }
    }
}

/** Compact theme picker for screen headers. */
@Composable
fun ThemeMenu(session: Session) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Theme: ${session.theme.label}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppTheme.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { session.selectTheme(t); open = false })
            }
        }
    }
}
