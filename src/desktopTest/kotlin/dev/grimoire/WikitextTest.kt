package dev.grimoire

import dev.grimoire.wiki.Wikitext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs against the real recorded Teleportation page from powerlisting.fandom.com. */
class WikitextTest {

    private val wikitext: String by lazy {
        val raw = javaClass.classLoader.getResourceAsStream("teleportation.json")!!.readBytes().decodeToString()
        Json.parseToJsonElement(raw).jsonObject
            .getValue("query").jsonObject.getValue("pages").jsonArray.first().jsonObject
            .getValue("revisions").jsonArray.first().jsonObject
            .getValue("slots").jsonObject.getValue("main").jsonObject
            .getValue("content").jsonPrimitive.content
    }

    @Test
    fun `infobox paired rows become attributes, presentation keys are dropped`() {
        val attrs = Wikitext.infobox(wikitext)
        assertEquals(
            "Instantly transport oneself from one place to another without traversing the physical space in between",
            attrs["Power/Ability to"],
        )
        assertFalse(attrs.keys.any { it.contains("image", true) || it.contains("bgcolor", true) })
    }

    @Test
    fun `the page body converts to the markdown our preview speaks`() {
        val md = Wikitext.toMarkdown(wikitext)
        assertTrue("## Also Called" in md, "headings convert")
        assertTrue("- Phase-Jumping" in md, "bullets convert")
        assertTrue("> Wow... he's fast!" in md, "Quote templates become blockquotes")
        assertTrue("**instantaneously move from one location to another" in md, "bold converts")
        assertTrue("[Blink](https://leagueoflegends.fandom.com/wiki/Blink)" in md, "external links become md links")
        assertTrue("Sub-power of Spatial Manipulation" in md, "wiki links flatten to text")
        assertFalse("{{" in md, "no templates survive")
        assertFalse("[[" in md, "no wiki brackets survive")
        assertFalse("<gallery" in md.lowercase(), "galleries dropped")
    }

    @Test
    fun `the intro paragraph becomes the summary, skipping quotes and headings`() {
        val summary = Wikitext.firstParagraph(Wikitext.toMarkdown(wikitext))
        assertTrue(summary.startsWith("The power to **instantaneously move"), summary.take(80))
        assertFalse(summary.contains(">"))
    }

    @Test
    fun `namespaced bare links keep only the page name`() {
        assertEquals("## X\nsee Apparition and teleport", Wikitext.toMarkdown("==X==\nsee [[w:c:harrypotter:Apparition]] and [[wikipedia:teleport]]"))
    }

    // --- the messier fixture: V (character), cyberpunk.fandom.com ---

    private val vWikitext: String by lazy {
        val raw = javaClass.classLoader.getResourceAsStream("v-character.json")!!.readBytes().decodeToString()
        Json.parseToJsonElement(raw).jsonObject
            .getValue("query").jsonObject.getValue("pages").jsonArray.first().jsonObject
            .getValue("revisions").jsonArray.first().jsonObject
            .getValue("slots").jsonObject.getValue("main").jsonObject
            .getValue("content").jsonPrimitive.content
    }

    @Test
    fun `named-param character infoboxes become attributes with refs stripped`() {
        val attrs = Wikitext.infobox(vWikitext)
        assertEquals("Valerie/Vincent", attrs["Title"])
        assertEquals("23 (as of April 2077)", attrs["Age"], "refs and links cleaned")
        assertEquals("October 12, 2053", attrs["Dob"])
        assertTrue(attrs["Aka"]!!.startsWith("V; Hannah/Harry Conwell"), "br-separated lists become ; lists")
        assertTrue(attrs["Place of birth"]!!.contains("American wilderness (Nomad)"), "underscored keys prettify")
        assertTrue(attrs["Partner"]!!.contains("Judy Álvarez (female V)"))
        assertFalse(attrs.keys.any { it.equals("image", true) })
        assertFalse(attrs.containsKey("Appears other"), "empty values dropped")
        assertFalse(attrs.containsKey("Appears games"), "template-only values dropped, not emitted as punctuation")
    }

    @Test
    fun `named-param Block Quote templates become blockquotes and the intro survives`() {
        val md = Wikitext.toMarkdown(vWikitext)
        assertTrue("> V. Just V. Only people who know me real well can use my real name." in md)
        assertTrue("> — V" in md)
        val summary = Wikitext.firstParagraph(md)
        assertTrue(summary.startsWith("**V**, an alias for"), summary.take(60))
        assertTrue("mercenary" in summary)
        assertFalse("{{" in md)
    }
}
