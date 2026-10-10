package dev.grimoire

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Source-level guards for the design system: screens build on ui/design, not on stock widgets. */
class UiConventionsTest {
    private val ui = File("src/commonMain/kotlin/dev/grimoire/ui")

    private fun screens(): List<File> =
        ui.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.path.replace('\\', '/').contains("/ui/design/") }.toList()

    @Test
    fun `no legacy widgets in screens`() {
        val banned = listOf("OutlinedTextField(", "NavigationRail", "VerticalDivider(")
        val offenders = screens().flatMap { f ->
            val text = f.readText()
            banned.filter { it in text }.map { "${f.name}: $it" }
        }
        assertTrue(offenders.isEmpty(), "legacy widgets still used:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `hex colours only in theme files`() {
        val allowed = setOf("Theme.kt", "TextChatView.kt")
        val offenders = screens().filter { it.name !in allowed && "Color(0x" in it.readText() }.map { it.name }
        assertTrue(offenders.isEmpty(), "hard-coded colours outside theme files: $offenders")
    }
}
