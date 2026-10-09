package dev.grimoire

import dev.grimoire.db.LinksFor
import dev.grimoire.entity.EntityType
import dev.grimoire.settings.SettingsStore
import dev.grimoire.template.TemplateFields
import dev.grimoire.template.TemplateField
import dev.grimoire.ui.EntityEditorState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntityEditorStateTest {
    private val settings = object : SettingsStore {
        override fun load() = emptyMap<String, String>()
        override fun save(values: Map<String, String>) = Unit
    }
    private val session = testSession(settings)

    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }

    private val world: String
    private val ws: dev.grimoire.ui.WorkspaceState
    private val characterSheet: String
    private val factionSheet: String
    private val raceTemplate: String

    init {
        session.openWorld(run { session.worlds.create("Arda") })
        ws = session.workspace!!
        world = ws.worldId
        characterSheet = run { session.templates.byBuiltinKey("character")!!.id }
        factionSheet = run { session.templates.byBuiltinKey("faction")!!.id }
        raceTemplate = run { session.templates.byBuiltinKey("concept.race")!!.id }
    }

    private fun entity(type: EntityType, name: String, template: String? = null, attrs: Map<String, String> = emptyMap()): String = run {
        val id = session.entities.create(world, type, name)
        if (template != null || attrs.isNotEmpty()) {
            session.entities.updateContent(id, name, emptyList(), null, null, template, attrs)
        }
        id
    }

    private fun editor(id: String): EntityEditorState {
        ws.open(id)
        await { ws.editor?.id == id && ws.editor?.loaded == true }
        return ws.editor!!
    }

    private fun field(e: EntityEditorState, template: String, key: String): TemplateField =
        run { TemplateFields.decode(session.templates.template(template).first()!!.fields).first { it.key == key } }

    private fun names(rows: List<LinksFor>) = rows.map { it.other_name }

    @Test
    fun `race and faction fields split member_of by target`() {
        val kaede = entity(EntityType.CHARACTER, "Kaede", characterSheet)
        val elf = entity(EntityType.CONCEPT, "Elf", raceTemplate)
        val hand = entity(EntityType.FACTION, "Silver Hand")
        val e = editor(kaede)
        val race = field(e, characterSheet, "race")
        val faction = field(e, characterSheet, "faction")
        e.addLink(race, elf)
        e.addLink(faction, hand)
        await { run { e.links.first() }.size == 2 }
        val all = run { e.links.first() }
        assertEquals(listOf("Elf"), names(e.linksOf(race, all)))
        assertEquals(listOf("Silver Hand"), names(e.linksOf(faction, all)))
        val rows = run { session.relationships.forEntity(kaede).first() }
        assertEquals(2, rows.size)
        assert(rows.all { it.kind == "member_of" })
        val raw = run { session.relationships.linksNow(kaede) }
        assert(raw.all { it.dir == "out" })
    }

    @Test
    fun `incoming link fields read the other direction`() {
        val kaede = entity(EntityType.CHARACTER, "Kaede", characterSheet)
        val hand = entity(EntityType.FACTION, "Silver Hand", factionSheet)
        val h = editor(hand)
        val members = field(h, factionSheet, "members")
        h.addLink(members, kaede)
        await { run { h.links.first() }.isNotEmpty() }
        val fromKaede = run { session.relationships.linksNow(kaede) }.single()
        assertEquals("out", fromKaede.dir) // kaede -> silverHand
        assertEquals("member_of", fromKaede.kind)
        assertEquals(listOf("Kaede"), names(h.linksOf(members, run { h.links.first() })))
        val k = editor(kaede)
        val faction = field(k, characterSheet, "faction")
        assertEquals(listOf("Silver Hand"), names(k.linksOf(faction, run { k.links.first() })))
    }

    @Test
    fun `linkTargets only offers filtered entities`() {
        val kaede = entity(EntityType.CHARACTER, "Kaede", characterSheet)
        entity(EntityType.CONCEPT, "Elf", raceTemplate)
        entity(EntityType.CONCEPT, "Elvish")
        val e = editor(kaede)
        val race = field(e, characterSheet, "race")
        assertEquals(listOf("Elf"), run { e.linkTargets(race, "El").first() }.map { it.name })
    }

    @Test
    fun `soft-deleted target disappears from chips`() {
        val kaede = entity(EntityType.CHARACTER, "Kaede", characterSheet)
        val elf = entity(EntityType.CONCEPT, "Elf", raceTemplate)
        val e = editor(kaede)
        val race = field(e, characterSheet, "race")
        e.addLink(race, elf)
        await { run { e.links.first() }.size == 1 }
        run { session.entities.softDelete(elf) }
        assertEquals(emptyList(), e.linksOf(race, run { e.links.first() }))
        // The relationship row itself is untouched: restoring the entity brings the chip back.
        run { session.entities.restore(elf) }
        assertEquals(listOf("Elf"), names(e.linksOf(race, run { e.links.first() })))
    }

    @Test
    fun `exactMatch links text`() {
        val kaede = entity(EntityType.CHARACTER, "Kaede", characterSheet, mapOf("race" to " elf "))
        val elf = entity(EntityType.CONCEPT, "Elf", raceTemplate)
        val e = editor(kaede)
        val race = field(e, characterSheet, "race")
        assertEquals(elf, run { e.exactMatch(race, " elf ") })
        entity(EntityType.CONCEPT, "Elf", raceTemplate)
        assertNull(run { e.exactMatch(race, " elf ") })
        assertNull(run { e.exactMatch(race, "nobody") })
    }

    @Test
    fun `picking a race fills the character and labels provenance`() {
        val religionTemplate = run { session.templates.byBuiltinKey("concept.religion")!!.id }
        val kaede = entity(EntityType.CHARACTER, "Kaede", characterSheet)
        val elf = entity(EntityType.CONCEPT, "Elf", raceTemplate)
        val sun = entity(EntityType.CONCEPT, "Sunworshippers", religionTemplate)
        val elfEditor = editor(elf)
        elfEditor.addLink(field(elfEditor, raceTemplate, "religion"), sun)
        await { run { session.relationships.linksNow(elf) }.size == 1 }

        val e = editor(kaede)
        val race = field(e, characterSheet, "race")
        val religion = field(e, characterSheet, "religion")
        e.addLink(race, elf)
        await { names(e.linksOf(religion, run { e.links.first() })) == listOf("Sunworshippers") }
        await { e.autofilledFrom["religion"] == "Elf" }
        e.editAttribute("religion", "x")
        assertNull(e.autofilledFrom["religion"])
    }

    @Test
    fun `scaffold only into blank notes`() {
        val blank = entity(EntityType.CHARACTER, "Blank")
        val scaffold = run { session.templates.template(characterSheet).first()!!.body_scaffold }!!
        val b = editor(blank)
        b.selectTemplate(characterSheet)
        await { b.body == scaffold } // scaffold is read from the template row, so it lands asynchronously

        val full = entity(EntityType.CHARACTER, "Full")
        val f = editor(full)
        f.editBody("x")
        f.selectTemplate(characterSheet)
        runBlocking { delay(300) } // give a (wrong) async scaffold write time to land
        assertEquals("x", f.body)
    }
}
