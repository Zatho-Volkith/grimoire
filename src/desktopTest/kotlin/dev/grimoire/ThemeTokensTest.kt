package dev.grimoire

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import dev.grimoire.ui.AppTheme
import dev.grimoire.ui.design.GrimoireColors
import dev.grimoire.ui.design.GrimoireFonts
import dev.grimoire.ui.design.grimoireTypography
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThemeTokensTest {

    @Test
    fun `forest is a theme with the spec palette`() {
        val f = AppTheme.fromKey("forest")
        assertEquals(AppTheme.FOREST, f)
        assertEquals(Color(0xFF1B2F22), f.colors.background)
        assertEquals(Color(0xFFE8963F), f.colors.primary)
        assertEquals(Color(0xFF22392A), f.extra.card)
        assertEquals(Color(0xFF2E4636), f.extra.field)
        assertEquals(Color(0xFF34503E), f.extra.border)
        assertEquals(Color(0xFFE3B95F), f.extra.muted)
    }

    @Test
    fun `saved keys still resolve`() {
        assertEquals(AppTheme.GRIMOIRE, AppTheme.fromKey("grimoire"))
        assertEquals(AppTheme.PARCHMENT, AppTheme.fromKey("parchment"))
        assertEquals(AppTheme.MIDNIGHT, AppTheme.fromKey("midnight"))
        assertEquals(AppTheme.IVORY, AppTheme.fromKey("ivory"))
        assertEquals(AppTheme.GRIMOIRE, AppTheme.fromKey("nope"))
        assertEquals(AppTheme.GRIMOIRE, AppTheme.fromKey(null))
    }

    @Test
    fun `derive follows the spec formulas`() {
        val red = Color(0xFFFF0000)
        val s = darkColorScheme(background = Color.Black, onBackground = Color.White, primary = red)
        val g = GrimoireColors.derive(s)
        assertEquals(lerp(Color.Black, Color.White, 0.05f), g.card)
        assertEquals(lerp(Color.Black, Color.White, 0.10f), g.field)
        assertEquals(lerp(Color.Black, Color.White, 0.14f), g.border)
        assertEquals(lerp(red, Color.White, 0.35f), g.muted)
        assertEquals(red.copy(alpha = 0.16f), g.selected)
    }

    @Test
    fun `captions stay legible on cards in every theme`() {
        fun contrast(a: Color, b: Color): Float {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05f) / (lo + 0.05f)
        }
        AppTheme.entries.forEach { t ->
            val c = contrast(t.extra.muted, t.extra.card)
            assertTrue(c >= 3.0f, "${t.key}: muted on card contrast $c < 3.0")
        }
    }

    @Test
    fun `placeholders stay legible on fields in every theme`() {
        fun contrast(a: Color, b: Color): Float {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05f) / (lo + 0.05f)
        }
        AppTheme.entries.forEach { t ->
            val c = contrast(dev.grimoire.ui.design.hintColor(t.colors, t.extra).compositeOver(t.extra.field), t.extra.field)
            assertTrue(c >= 3.0f, "${t.key}: placeholder on field contrast $c < 3.0")
        }
    }

    @Test
    fun `typography uses serif for titles and sans for body`() {
        val f = GrimoireFonts.System
        val t = grimoireTypography(f)
        assertEquals(f.serif, t.titleLarge.fontFamily)
        assertEquals(f.sans, t.bodyMedium.fontFamily)
        assertEquals(14.sp, t.bodyMedium.fontSize)
        assertEquals(26.sp, t.headlineMedium.fontSize)
        assertEquals(18.sp, t.titleMedium.fontSize)
    }

    @Test
    fun `font loading falls back`() {
        assertEquals(GrimoireFonts.System, DesktopFonts.load(resourcePrefix = "missing/"))
    }

    @Test
    fun `bundled fonts load`() {
        assertNotEquals(GrimoireFonts.System, DesktopFonts.load())
    }
}
