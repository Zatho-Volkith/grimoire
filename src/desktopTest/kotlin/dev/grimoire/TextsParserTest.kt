package dev.grimoire

import dev.grimoire.work.TextChat
import dev.grimoire.work.TextItem.Message
import dev.grimoire.work.TextItem.Notice
import dev.grimoire.work.TextItem.Receipt
import dev.grimoire.work.Texts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextsParserTest {

    private val fullLines = listOf(
        "@me Sam",
        "Sam: you up?",
        "Alex: unfortunately",
        "Sam: same",
        "~ Read 2:15 AM",
        "-- Alex added Jordan to the chat",
        "Jordan: what did I miss",
    )

    private val full = TextChat(
        "Alex", "Sam",
        listOf(
            Message("Sam", "you up?"),
            Message("Alex", "unfortunately"),
            Message("Sam", "same"),
            Receipt("Read 2:15 AM"),
            Notice("Alex added Jordan to the chat"),
            Message("Jordan", "what did I miss"),
        ),
    )

    /** Parses a serialized block the way Markdown hands it over: title from the fence, body lines between. */
    private fun reparse(block: String): TextChat {
        val lines = block.split('\n')
        return Texts.parse(Texts.fenceTitle(lines.first())!!, lines.drop(1).dropLast(1))
    }

    @Test
    fun `every line type`() {
        assertEquals(full, Texts.parse("Alex", fullLines))
    }

    @Test
    fun `group detection`() {
        assertTrue(full.isGroup)
        assertFalse(Texts.parse("Alex", fullLines.dropLast(1)).isGroup)
        assertTrue(Texts.parse("", listOf("A: hi", "B: yo")).isGroup)
    }

    @Test
    fun `fence title`() {
        assertEquals("Alex", Texts.fenceTitle("::: texts Alex"))
        assertEquals("", Texts.fenceTitle("  :::texts  "))
        assertNull(Texts.fenceTitle("::: textsy"))
        assertNull(Texts.fenceTitle(":::note"))
        assertNull(Texts.fenceTitle("hello"))
        assertTrue(Texts.isCloseFence(" ::: "))
        assertFalse(Texts.isCloseFence("::: texts"))
    }

    @Test
    fun `unmatched and blank lines`() {
        val longName = "${"a".repeat(41)}: x"
        assertEquals(
            listOf(Message(null, "just words"), Message(null, longName)),
            Texts.parse("", listOf("just words", "", "   ", longName)).items,
        )
    }

    @Test
    fun `last @me wins`() {
        assertEquals("Jo", Texts.parse("", listOf("@me Sam", "@me Jo")).owner)
    }

    @Test
    fun `line breaks`() {
        assertEquals(Message("Sam", "one\ntwo"), Texts.parse("", listOf("""Sam: one\ntwo""")).items.single())
        assertEquals(Message("Sam", """path\\n"""), Texts.parse("", listOf("""Sam: path\\n""")).items.single())
    }

    @Test
    fun `leading backslash forces plain message`() {
        assertEquals(Message(null, "-- not a notice"), Texts.parse("", listOf("""\-- not a notice""")).items.single())
    }

    @Test
    fun `serialize writes the canonical block`() {
        assertEquals(
            "::: texts Alex\n" + fullLines.joinToString("\n") + "\n:::",
            Texts.serialize(full),
        )
        assertEquals("::: texts\nhi\n:::", Texts.serialize(TextChat("", null, listOf(Message(null, "hi")))))
    }

    @Test
    fun `round trip`() {
        val chats = listOf(
            full,
            TextChat("", null, listOf(Message("A", "hi"), Notice("x"))),
            TextChat("Grp", "Sam", listOf(Message("Sam", "line one\nline two"), Message("Jo", """keep \\n literal"""))),
            TextChat("", null, listOf(Message(null, "-- yeah right"), Message(null, "Sam: hi"), Message(null, "~ x"), Message(null, "@me x"), Message(null, ":::"), Message(null, """\lead"""))),
        )
        chats.forEach { assertEquals(it, reparse(Texts.serialize(it))) }
    }

    @Test
    fun `a lone backslash before n or a break is written as an escaped backslash`() {
        val typed = TextChat("", null, listOf(Message("A", """a\nb"""), Message("A", """end\""")))
        assertEquals(
            listOf(Message("A", """a\\nb"""), Message("A", """end\\""")),
            reparse(Texts.serialize(typed)).items,
        )
    }
}
