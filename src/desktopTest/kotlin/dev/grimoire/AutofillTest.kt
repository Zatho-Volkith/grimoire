package dev.grimoire

import dev.grimoire.db.LinksFor
import dev.grimoire.template.Autofill
import dev.grimoire.template.AutofillAction
import dev.grimoire.template.AutofillAction.AddLink
import dev.grimoire.template.AutofillAction.SetText
import dev.grimoire.template.AutofillRule
import dev.grimoire.template.AutofillSource
import dev.grimoire.template.BuiltinTemplates
import dev.grimoire.template.FieldType
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkSpec
import dev.grimoire.template.TemplateField
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutofillTest {
    private fun fields(key: String) = BuiltinTemplates.all.first { it.key == key }.fields
    private val character = fields("character")
    private val race = fields("concept.race")
    private val religion = fields("concept.religion")

    private var n = 0
    /** An edge seen from its owner: [kind]/[dir] to entity [id] named [name]. */
    private fun edge(kind: String, dir: String, id: String, name: String, type: String, builtin: String? = null) =
        LinksFor("r${n++}", kind, dir, id, name, type, builtin?.let { "t-$it" }, builtin)

    private val toElf = edge("member_of", "out", "elf", "Elf", "concept", "concept.race")
    private val toSun = edge("follows", "out", "sun", "Sunworshippers", "concept", "concept.religion")
    private val toSol = edge("worships", "out", "sol", "Sol", "character")
    private val toElvish = edge("speaks", "out", "elvish", "Elvish", "concept", "concept.language")
    private val toSilverwood = edge("located_in", "out", "silverwood", "Silverwood", "location")

    private fun plan(
        sources: Map<String, AutofillSource>,
        links: List<LinksFor> = listOf(toElf),
        attributes: Map<String, String> = emptyMap(),
        fields: List<TemplateField> = character,
        trigger: String = "race",
        selfId: String = "kaede",
    ): List<AutofillAction> = runBlocking {
        withTimeout(1_000) { Autofill.plan(selfId, fields, attributes, links, trigger) { sources[it] } }
    }

    @Test
    fun `race fills religion, deity, languages, homeland`() {
        val elf = AutofillSource("elf", "Elf", emptyMap(), race, listOf(toSun, toSol, toElvish, toSilverwood))
        val actions = plan(mapOf("elf" to elf))
        assertEquals(
            listOf(
                AddLink("religion", "sun", "Elf"),
                AddLink("deity", "sol", "Elf"),
                AddLink("languages", "elvish", "Elf"),
                AddLink("homeland", "silverwood", "Elf"),
            ),
            actions,
        )
    }

    private val sunSource = AutofillSource("sun", "Sunworshippers", emptyMap(), religion, listOf(toSol))
    private val elfReligionOnly = AutofillSource("elf", "Elf", emptyMap(), race, listOf(toSun))

    @Test
    fun `chain race to religion to deity`() {
        val actions = plan(mapOf("elf" to elfReligionOnly, "sun" to sunSource))
        val religionAt = actions.indexOf(AddLink("religion", "sun", "Elf"))
        val deityAt = actions.indexOf(AddLink("deity", "sol", "Sunworshippers"))
        assertTrue(religionAt >= 0 && deityAt > religionAt, actions.toString())
    }

    @Test
    fun `first rule empty falls back to second`() {
        val actions = plan(mapOf("elf" to elfReligionOnly, "sun" to sunSource))
        // race.deity is empty on Elf, so the deity comes through religion.deities.
        assertEquals(listOf(AddLink("deity", "sol", "Sunworshippers")), actions.filter { it.fieldKey == "deity" })
    }

    @Test
    fun `filled fields are never touched`() {
        val elf = AutofillSource("elf", "Elf", emptyMap(), race, listOf(toSun, toSol, toElvish))
        val ownDeity = edge("worships", "out", "luna", "Luna", "character")
        val actions = plan(mapOf("elf" to elf), links = listOf(toElf, ownDeity), attributes = mapOf("religion" to "lapsed"))
        assertTrue(actions.none { it.fieldKey == "religion" || it.fieldKey == "deity" }, actions.toString())
        assertEquals(listOf(AddLink("languages", "elvish", "Elf")), actions)
    }

    @Test
    fun `self link is skipped`() {
        val toSelf = edge("worships", "out", "kaede", "Kaede", "character")
        val elf = AutofillSource("elf", "Elf", emptyMap(), race, listOf(toSelf, toElvish))
        val actions = plan(mapOf("elf" to elf))
        assertTrue(actions.none { it is AddLink && it.targetId == "kaede" }, actions.toString())
        assertEquals(listOf(AddLink("languages", "elvish", "Elf")), actions)
    }

    @Test
    fun `text source into link field becomes text`() {
        val textRace = race.map { if (it.key == "languages") TemplateField("languages", "Languages") else it }
        val elf = AutofillSource("elf", "Elf", mapOf("languages" to "Old Tongue"), textRace, emptyList())
        assertEquals(listOf(SetText("languages", "Old Tongue", "Elf")), plan(mapOf("elf" to elf)))
    }

    @Test
    fun `city ruler from nation`() {
        val toEldoria = edge("located_in", "out", "eldoria", "Eldoria", "location", "location.nation")
        val fromAna = edge("leads", "in", "ana", "Queen Ana", "character")
        val eldoria = AutofillSource("eldoria", "Eldoria", emptyMap(), fields("location.nation"), listOf(fromAna))
        val actions = plan(
            mapOf("eldoria" to eldoria), links = listOf(toEldoria),
            fields = fields("location.city"), trigger = "located_in", selfId = "port",
        )
        assertEquals(listOf(AddLink("ruler", "ana", "Eldoria")), actions)
    }

    @Test
    fun `loops terminate`() {
        fun f(key: String, other: String) = TemplateField(
            key, key, FieldType.LINK, link = LinkSpec("ally_of", LinkDir.OUT, targetTemplate = key),
            autofill = listOf(AutofillRule(other, key)),
        )
        val loop = listOf(f("a", "b"), f("b", "a"))
        val toX = edge("ally_of", "out", "x", "X", "faction", "a")
        val toY = edge("ally_of", "out", "y", "Y", "faction", "b")
        // X's a-field points at Y and vice versa — the walk keeps finding new work until visited stops it.
        val sources = mapOf(
            "x" to AutofillSource("x", "X", emptyMap(), loop, listOf(toY)),
            "y" to AutofillSource("y", "Y", emptyMap(), loop, listOf(toX)),
        )
        plan(sources, links = listOf(toX), fields = loop, trigger = "a")
    }
}
