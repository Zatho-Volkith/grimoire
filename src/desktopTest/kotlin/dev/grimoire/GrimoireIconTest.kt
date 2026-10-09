package dev.grimoire

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dev.grimoire.ui.AppTheme
import dev.grimoire.ui.design.GrimoireIcon
import dev.grimoire.ui.design.IconLoader
import dev.grimoire.ui.design.IconSlot
import dev.grimoire.ui.design.LocalGrimoireColors
import dev.grimoire.ui.design.LocalIconLoader
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrimoireIconTest {
    @get:Rule val rule = createComposeRule()

    private fun themed(loader: IconLoader, content: @Composable () -> Unit) = rule.setContent {
        MaterialTheme(colorScheme = AppTheme.GRIMOIRE.colors) {
            CompositionLocalProvider(LocalGrimoireColors provides AppTheme.GRIMOIRE.extra, LocalIconLoader provides loader) {
                content()
            }
        }
    }

    @Test
    fun `missing icon draws the placeholder`() {
        themed(IconLoader { null }) { GrimoireIcon(IconSlot.CHARACTER) }
        rule.onNodeWithTag("icon-character", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("icon-placeholder", useUnmergedTree = true).assertExists()
        rule.onNode(hasTestTag("icon-placeholder") and hasText("C"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `loaded icon replaces the placeholder`() {
        themed(IconLoader { ColorPainter(Color.Red) }) { GrimoireIcon(IconSlot.CHARACTER) }
        rule.onNodeWithTag("icon-character", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("icon-placeholder", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `malformed svg falls back`() {
        assertNull(DesktopIcons(resourcePrefix = "icons-bad/").load(IconSlot.CHARACTER))
    }

    @Test
    fun `absent svg falls back`() {
        assertNull(DesktopIcons().load(IconSlot.CHARACTER))
    }

    @Test
    fun `every slot has a file name`() {
        val names = IconSlot.entries.map { it.fileName }
        assertTrue(names.all { it.endsWith(".svg") })
        assertEquals(names.size, names.toSet().size)
        assertEquals("character.svg", IconSlot.CHARACTER.fileName)
    }
}
