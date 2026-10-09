package dev.grimoire

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import dev.grimoire.ui.design.GrimoireFonts

/** Loads the bundled EB Garamond + Inter (SIL OFL, see fonts/<family>/OFL.txt). */
object DesktopFonts {

    /** Falls back to [GrimoireFonts.System] if any file is missing or unreadable — never crashes start-up. */
    fun load(resourcePrefix: String = "fonts/"): GrimoireFonts = runCatching {
        fun font(path: String, weight: FontWeight) = Font(resource = resourcePrefix + path, weight = weight).also {
            // Font() is lazy; touch the resource now so a missing file falls back here, not mid-render.
            checkNotNull(javaClass.classLoader.getResource(resourcePrefix + path)) { "missing $path" }
        }
        GrimoireFonts(
            serif = FontFamily(
                font("eb-garamond/EBGaramond-Regular.ttf", FontWeight.Normal),
                font("eb-garamond/EBGaramond-SemiBold.ttf", FontWeight.SemiBold),
            ),
            sans = FontFamily(
                font("inter/Inter-Regular.ttf", FontWeight.Normal),
                font("inter/Inter-Medium.ttf", FontWeight.Medium),
                font("inter/Inter-SemiBold.ttf", FontWeight.SemiBold),
            ),
        )
    }.getOrDefault(GrimoireFonts.System)
}
