package dev.grimoire.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Colour roles Material3's [ColorScheme] doesn't have: card and field surfaces, hairline borders,
 * accent-tinted captions, and the selected-row tint. Every theme provides one; most derive it.
 */
data class GrimoireColors(
    val card: Color,
    val field: Color,
    val border: Color,
    val muted: Color,
    val selected: Color,
) {
    companion object {
        fun derive(s: ColorScheme): GrimoireColors = GrimoireColors(
            card = lerp(s.background, s.onBackground, 0.05f),
            field = lerp(s.background, s.onBackground, 0.10f),
            border = lerp(s.background, s.onBackground, 0.14f),
            muted = lerp(s.primary, s.onBackground, 0.35f),
            selected = s.primary.copy(alpha = 0.16f),
        )
    }
}

val LocalGrimoireColors: ProvidableCompositionLocal<GrimoireColors> =
    staticCompositionLocalOf { error("GrimoireColors not provided — wrap content in App's theme") }

/** `MaterialTheme.grimoire.card` etc., next to `MaterialTheme.colorScheme`. */
val MaterialTheme.grimoire: GrimoireColors
    @Composable @ReadOnlyComposable get() = LocalGrimoireColors.current
