package dev.grimoire

import dev.grimoire.work.Ao3WorkSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AO3 silently drops anything outside its skin whitelist — keep the stylesheet inside it. */
class Ao3WorkSkinTest {

    private val css = Ao3WorkSkin.css
    private val rule = Regex("""([^{}]+)\{([^{}]*)\}""")
    private val allowed = setOf(
        "display", "flex-direction", "align-self", "max-width", "margin", "margin-bottom", "padding",
        "padding-bottom", "background-color", "color", "border", "border-bottom", "border-radius",
        "font-size", "font-weight", "line-height", "text-align",
    )

    private fun declarations(selector: String): Map<String, String> =
        rule.findAll(css).single { it.groupValues[1].trim() == selector }.groupValues[2]
            .split(';').map { it.trim() }.filter { it.isNotEmpty() }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }

    @Test
    fun `uses only AO3-safe css`() {
        listOf("/*", "var(", "font:", "rem", "vw").forEach { assertFalse(it in css, "contains $it") }
        val rules = rule.findAll(css).toList()
        assertTrue(rules.size >= 10)
        rules.forEach { r ->
            assertTrue(r.groupValues[1].trim().startsWith("#workskin .gt-"), r.groupValues[1])
            val props = r.groupValues[2].split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { it.substringBefore(':').trim() }
            assertEquals(props.size, props.toSet().size, "repeated property in ${r.groupValues[1]}")
            props.forEach { assertTrue(it in allowed, "disallowed property $it") }
        }
        assertEquals(css.length, rules.sumOf { it.value.length }, "nothing outside rules")
    }

    @Test
    fun `colours come from the palette`() {
        listOf("#0B5FCC", "#E9E9EB", "#1C1C1E", "#6C6C70", "#D1D1D6", "#FFFFFF").forEach { assertTrue(it in css, it) }
    }

    @Test
    fun `names hidden except in group chats`() {
        assertEquals("none", declarations("#workskin .gt-name")["display"])
        assertEquals("block", declarations("#workskin .gt-group .gt-them .gt-name")["display"])
    }
}
