package dev.grimoire

import dev.grimoire.wiki.TextDiff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextDiffTest {

    @Test
    fun `identical texts and blank-only differences produce no hunks`() {
        assertTrue(TextDiff.hunks("a\nb\nc", "a\nb\nc").isEmpty())
        assertTrue(TextDiff.hunks("a\n\nb", "a\nb").isEmpty(), "blank-line churn is not a disagreement")
    }

    @Test
    fun `a single edited line in a long document is one small hunk`() {
        val canon = (1..200).joinToString("\n") { "line $it" }
        val yours = canon.replace("line 100", "line one hundred, rewritten")
        val hunks = TextDiff.hunks(canon, yours)
        assertEquals(1, hunks.size)
        assertEquals(listOf("line 100"), hunks.single().canon)
        assertEquals(listOf("line one hundred, rewritten"), hunks.single().yours)
    }

    @Test
    fun `additions and deletions keep their sides`() {
        val hunks = TextDiff.hunks("keep\nold only\nkeep2", "keep\nkeep2\nnew only")
        assertEquals(2, hunks.size)
        assertEquals(listOf("old only") to emptyList(), hunks[0].canon to hunks[0].yours)
        assertEquals(emptyList<String>() to listOf("new only"), hunks[1].canon to hunks[1].yours)
    }

    @Test
    fun `two separate edits become two hunks with the unchanged middle hidden`() {
        val canon = "alpha\nsame1\nsame2\nsame3\nomega"
        val yours = "ALPHA\nsame1\nsame2\nsame3\nOMEGA"
        val hunks = TextDiff.hunks(canon, yours)
        assertEquals(2, hunks.size)
        assertEquals(listOf("alpha") to listOf("ALPHA"), hunks[0].canon to hunks[0].yours)
        assertEquals(listOf("omega") to listOf("OMEGA"), hunks[1].canon to hunks[1].yours)
    }
}
