package dev.grimoire.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The two families the UI uses: a bookish serif for titles, a compact sans for everything else.
 * Desktop loads the bundled EB Garamond + Inter; [System] is the fallback (and what tests use).
 */
data class GrimoireFonts(val serif: FontFamily, val sans: FontFamily) {
    companion object {
        val System = GrimoireFonts(FontFamily.Serif, FontFamily.SansSerif)
    }
}

/** Page title 26 / card title 18 in serif; body 14, labels and captions 12, section labels 11 in sans. */
fun grimoireTypography(fonts: GrimoireFonts): Typography {
    val serif = TextStyle(fontFamily = fonts.serif)
    val sans = TextStyle(fontFamily = fonts.sans)
    return Typography(
        displayLarge = serif.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
        displayMedium = serif.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
        displaySmall = serif.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
        headlineLarge = serif.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = serif.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = serif.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = serif.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = serif.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = sans.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = sans.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = sans.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = sans.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = sans.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelMedium = sans.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = sans.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
    )
}
