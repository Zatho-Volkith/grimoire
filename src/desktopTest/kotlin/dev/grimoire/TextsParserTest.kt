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

    private val bodyWithBlock = "a\n\n::: texts X\nX: hi\n:::\n\nb"
    private val block = "::: texts X\nX: hi\n:::"

    @Test
    fun `block range at cursor`() {
        val inside = bodyWithBlock.indexOf("hi")
        assertEquals(block, bodyWithBlock.substring(Texts.blockRangeAt(bodyWithBlock, inside)!!))
        assertEquals(block, bodyWithBlock.substring(Texts.blockRangeAt(bodyWithBlock, bodyWithBlock.lastIndexOf(":::") + 3)!!))
        assertEquals(block, bodyWithBlock.substring(Texts.blockRangeAt(bodyWithBlock, bodyWithBlock.indexOf(":::"))!!))
        assertNull(Texts.blockRangeAt(bodyWithBlock, 0))
        assertNull(Texts.blockRangeAt(bodyWithBlock, bodyWithBlock.length))

        val unclosed = "::: texts\nA: x"
        assertEquals(unclosed.indices, Texts.blockRangeAt(unclosed, unclosed.length))

        val crlf = bodyWithBlock.replace("\n", "\r\n")
        val found = crlf.substring(Texts.blockRangeAt(crlf, crlf.indexOf("hi"))!!)
        assertTrue(found.startsWith(":::") && found.endsWith(":::"), found)
    }

    @Test
    fun `splice inserts as its own block`() {
        val b = "::: texts\nhi\n:::"
        assertEquals("one\n\n$b\n\n two" to "one\n\n$b".length, Texts.splice("one two", null, 3, b))
        assertEquals(b to b.length, Texts.splice("", null, 0, b))
        assertEquals("a\n\n$b\n\nb", Texts.splice("a\n\nb", null, 3, b).first)
    }

    @Test
    fun `splice replaces an existing block`() {
        val b = "::: texts Y\nY: yo\n:::"
        val range = Texts.blockRangeAt(bodyWithBlock, bodyWithBlock.indexOf("hi"))
        assertEquals("a\n\n$b\n\nb", Texts.splice(bodyWithBlock, range, 0, b).first)
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
