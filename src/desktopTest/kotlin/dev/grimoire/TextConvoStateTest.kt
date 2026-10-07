package dev.grimoire

import dev.grimoire.ui.RowKind
import dev.grimoire.ui.TextConvoState
import dev.grimoire.work.TextChat
import dev.grimoire.work.TextItem.Message
import dev.grimoire.work.TextItem.Notice
import dev.grimoire.work.TextItem.Receipt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextConvoStateTest {

    private val chat = TextChat(
        "Alex", "Sam",
        listOf(
            Message("Sam", "you up?"), Message("Alex", "unfortunately"), Message("Sam", "same"),
            Receipt("Read 2:15 AM"), Notice("Alex added Jordan to the chat"), Message("Jordan", "what did I miss"),
        ),
    )

    private fun convo() = TextConvoState(null, emptyList()).apply { editTitle("Alex"); editOwner("Sam") }

    /** Adds a message after the last row and gives it [sender]; returns the prefilled sender it got. */
    private fun TextConvoState.say(sender: String? = null): String {
        val key = addMessage()
        val prefilled = rows.single { it.key == key }.sender
        if (sender != null) editSender(key, sender)
        editText(key, "x")
        return prefilled
    }

    @Test
    fun `round trips an existing chat`() {
        assertEquals(chat, TextConvoState(chat, emptyList()).toChat())
    }

    @Test
    fun `alternating sender`() {
        assertEquals("Sam", convo().say())                                    // new chat → owner
        assertEquals("Alex", convo().apply { say("Sam") }.say())              // after the owner → the contact
        assertEquals("Sam", convo().apply { say("Sam"); say("Alex") }.say())  // back and forth
        assertEquals("Alex", convo().apply { say("Sam"); say("Alex"); say("Jordan") }.say())
        assertEquals("Sam", convo().apply { say("Alex"); say("Alex") }.say()) // double text → owner
    }

    @Test
    fun `adding after a row inserts there`() {
        val c = convo()
        val first = c.addMessage()
        c.addNotice()
        val inserted = c.addMessage(afterKey = first)
        assertEquals(listOf(RowKind.MESSAGE, RowKind.MESSAGE, RowKind.NOTICE), c.rows.map { it.kind })
        assertEquals(inserted, c.rows[1].key)
    }

    @Test
    fun `editing and ordering`() {
        val c = convo()
        val a = c.addMessage(); c.editText(a, "a")
        val b = c.addReceipt(); c.editText(b, "b")
        val n = c.addNotice(); c.editText(n, "n")

        c.move(n, -1)
        assertEquals(listOf("a", "n", "b"), c.rows.map { it.text })
        c.move(a, -1) // already first
        c.move(b, 1)  // already last
        assertEquals(listOf("a", "n", "b"), c.rows.map { it.text })

        c.remove(n)
        c.editSender(a, "Jo")
        assertEquals(listOf(Message("Jo", "a"), Receipt("b")), c.toChat()!!.items)
    }

    @Test
    fun `blank rows dropped, empty chat is null`() {
        val c = convo()
        c.addMessage(); c.addNotice()
        assertNull(c.toChat())
        val k = c.addMessage(); c.editText(k, "  hi  ")
        assertEquals(listOf(Message("Sam", "hi")), c.toChat()!!.items) // saved lines are trimmed on read anyway
    }

    @Test
    fun `sender suggestions`() {
        val c = TextConvoState(null, listOf("Mira", "Alex")).apply { editOwner("Sam") }
        val k = c.addMessage(); c.editSender(k, "Alex")
        assertEquals(listOf("Sam", "Alex", "Mira"), c.senderSuggestions)
    }

    @Test
    fun `handles keep their punctuation when it is harmless`() {
        val c = convo()
        listOf("@jordan", "-x", "~ann", "a-b").forEach { name ->
            val k = c.addMessage(); c.editText(k, "hi"); c.editSender(k, name)
        }
        assertEquals(listOf("@jordan", "-x", "~ann", "a-b"), c.toChat()!!.items.map { (it as Message).sender })
    }

    @Test
    fun `names that would read back as a marker are cleaned`() {
        val c = convo()
        listOf("@me Kim", "~ Lee", "\\Bo").forEach { name ->
            val k = c.addMessage(); c.editText(k, "hi"); c.editSender(k, name)
        }
        assertEquals(listOf("Kim", "Lee", "Bo"), c.toChat()!!.items.map { (it as Message).sender })
    }

    @Test
    fun `names are sanitised`() {
        val c = convo()
        val k = c.addMessage(); c.editText(k, "hi"); c.editSender(k, "  Dr: Who  ")
        val long = c.addMessage(); c.editText(long, "yo"); c.editSender(long, "n".repeat(50))
        val marker = c.addMessage(); c.editText(marker, "x"); c.editSender(marker, "-- Ghost")
        assertEquals(listOf("Dr:Who", "n".repeat(40), "Ghost"), c.toChat()!!.items.map { (it as Message).sender })
    }
}
