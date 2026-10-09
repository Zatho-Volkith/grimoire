package dev.grimoire

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import dev.grimoire.ui.AppTheme
import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GCard
import dev.grimoire.ui.design.GChip
import dev.grimoire.ui.design.GDialog
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.ListRow
import dev.grimoire.ui.design.LocalGrimoireColors
import dev.grimoire.ui.design.NavRow
import dev.grimoire.ui.design.PageHeader
import dev.grimoire.ui.design.SectionLabel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignKitTest {
    @get:Rule val rule = createComposeRule()

    private fun themed(content: @Composable () -> Unit) = rule.setContent {
        MaterialTheme(colorScheme = AppTheme.FOREST.colors) {
            CompositionLocalProvider(LocalGrimoireColors provides AppTheme.FOREST.extra) { content() }
        }
    }

    @Test
    fun `card shows title and caption`() {
        themed { GCard(title = "Relationships", caption = "Who this connects to") {} }
        rule.onNodeWithText("Relationships").assertIsDisplayed()
        rule.onNodeWithText("Who this connects to").assertIsDisplayed()
    }

    @Test
    fun `field label is above and edits flow`() {
        var seen = ""
        themed {
            var v by remember { mutableStateOf("") }
            GField(v, { v = it; seen = it }, label = "Name")
        }
        rule.onNode(hasSetTextAction() and hasText("Name")).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("Mira")
        rule.waitForIdle()
        assertEquals("Mira", seen)
    }

    @Test
    fun `buttons click and disable`() {
        var clicks = 0
        themed {
            Box {
                GButton("Create", { clicks++ }, kind = ButtonKind.PRIMARY)
            }
        }
        rule.onNodeWithText("Create").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `disabled button is not enabled`() {
        themed { GButton("Add", {}, enabled = false) }
        rule.onNodeWithText("Add").assertIsNotEnabled()
    }

    @Test
    fun `chip remove fires`() {
        var removed = false
        themed { GChip("Elf", onRemove = { removed = true }) }
        rule.onNodeWithTag("chip-remove").performClick()
        assertTrue(removed)
    }

    @Test
    fun `section label is uppercase`() {
        themed { SectionLabel("Fields") }
        rule.onNodeWithText("FIELDS").assertIsDisplayed()
    }

    @Test
    fun `nav row marks selection`() {
        themed { NavRow("Timeline", icon = null, selected = true, onClick = {}) }
        rule.onNodeWithText("Timeline", useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `long titles stay on one line and keep actions visible`() {
        val long = "Mira ".repeat(60)
        themed {
            Box(Modifier.width(400.dp)) {
                PageHeader(long, Modifier.testTag("header")) {
                    GButton("Delete", {}, Modifier.testTag("header-action"))
                }
            }
        }
        rule.onNodeWithTag("header-action").assertIsDisplayed()
        val w = rule.onNodeWithTag("page-title", useUnmergedTree = true).getUnclippedBoundsInRoot().width
        assertTrue(w <= 400.dp, "title width $w")
    }

    @Test
    fun `long list rows keep their trailing content`() {
        val long = "Josselin ".repeat(60)
        themed {
            Box(Modifier.width(280.dp)) {
                ListRow(long, {}, secondary = long) { GButton("✕", {}, Modifier.testTag("row-trailing")) }
            }
        }
        rule.onNodeWithTag("row-trailing").assertIsDisplayed()
    }

    @Test
    fun `dialog shows title and dismisses`() {
        var dismissed = false
        themed {
            GDialog("Sources", onDismiss = { dismissed = true }, actions = { GButton("Close", { dismissed = true }) }) {}
        }
        rule.onNodeWithText("Sources").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()
        assertTrue(dismissed)
    }
}
