package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.ui.VariantEditorState
import dev.grimoire.variant.VariantRepository
import dev.grimoire.work.WorkRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VariantRepositoryTest {
    private val ids = IdGenerator(Random(43)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val entities = EntityRepository(db, ids) { 1L }
    private val works = WorkRepository(db, ids) { 1L }
    private val variants = VariantRepository(db, ids) { 1L }

    @Test
    fun `one live variant per scope - duplicates return null, soft-delete frees the slot`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        val fic = works.create(w, "Fic")

        val v1 = variants.create(kaede, fic, null)
        assertNotNull(v1)
        assertNull(variants.create(kaede, fic, null), "second live variant for the same work")
        assertNotNull(variants.create(kaede, null, "canon-divergent AU"), "different scope is fine")

        variants.softDelete(v1)
        assertNotNull(variants.create(kaede, fic, null), "tombstoned scope can be re-created")
    }

    @Test
    fun `overrides resolve through entityWithVariant and blank means inherit`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        entities.updateContent(kaede, "Kaede", emptyList(), "A swordswoman.", "Canon notes.", null, emptyMap())
        val fic = works.create(w, "Fic")
        val v = variants.create(kaede, fic, null)!!

        val editor = VariantEditorState(v, variants, emptyMap(), backgroundScope)
        editor.load()
        assertEquals("In “Fic”", editor.scopeLabel)
        editor.editNameOverride("Kaede of the North")
        editor.editSummaryOverride("") // blank -> inherit canon
        editor.flush()

        val resolved = db.grimoireQueries.entityWithVariant(fic, kaede).executeAsOne()
        assertEquals("Kaede of the North", resolved.name_override)
        assertNull(resolved.summary_override, "blank override stored as NULL, canon shows through")
        assertEquals("A swordswoman.", resolved.summary)

        val listed = variants.forEntity(kaede).first().single()
        assertEquals("Fic" to "Kaede of the North", listed.work_title to listed.name_override)
    }

    @Test
    fun `attribute patches store only changed keys and blank clears back to inherit`() = runTest {
        val w = worlds.create("Arda")
        val kaede = entities.create(w, EntityType.CHARACTER, "Kaede")
        entities.updateContent(kaede, "Kaede", emptyList(), null, null, null, mapOf("age" to "27", "home" to "The North"))
        val fic = works.create(w, "Fic")
        val v = variants.create(kaede, fic, null)!!

        val editor = VariantEditorState(v, variants, mapOf("age" to "27", "home" to "The North"), backgroundScope)
        editor.load()
        assertEquals(listOf("age", "home"), editor.patchKeys)

        editor.editPatch("age", "35")
        editor.editPatch("home", "Night City")
        editor.editPatch("home", "") // changed my mind: inherit again
        editor.flush()

        val row = variants.forEntity(kaede).first().single()
        assertEquals("""{"age":"35"}""", row.attribute_patch, "only the changed key persists")
        assertEquals(
            """{"age":"35"}""",
            db.grimoireQueries.entityWithVariant(fic, kaede).executeAsOne().attribute_patch,
            "resolution query carries the patch",
        )

        val editor2 = VariantEditorState(v, variants, mapOf("age" to "27"), backgroundScope)
        editor2.load()
        assertEquals(mapOf("age" to "35"), editor2.patch)
    }
}
