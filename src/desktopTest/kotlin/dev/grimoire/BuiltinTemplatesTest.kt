package dev.grimoire

import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.template.BuiltinTemplates
import dev.grimoire.template.TemplateFields
import dev.grimoire.template.TemplateRepository
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltinTemplatesTest {
    private val ids = IdGenerator(Random(61)) { 1_700_000_000_000L }
    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-test").resolve("g.db")))
    private val worlds = WorldRepository(db, ids) { 1L }
    private val templates = TemplateRepository(db, ids) { 1L }

    @Test
    fun `every entity type gets a global built-in that worlds see immediately`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")

        val forCharacters = templates.forType(w, EntityType.CHARACTER).first()
        assertEquals(listOf("Character sheet"), forCharacters.map { it.name })
        assertTrue(forCharacters.single().is_builtin)
        assertEquals(
            listOf("Age", "Pronouns", "Occupation", "Appearance", "Personality", "Wants", "Fears"),
            TemplateFields.decode(forCharacters.single().fields).map { it.label },
        )
        EntityType.entries.forEach { type ->
            assertEquals(1, templates.forType(w, type).first().size, "one built-in for ${type.key}")
        }
    }

    @Test
    fun `seeding is once-ever - even deleting a built-in doesn't bring it back`() = runTest {
        BuiltinTemplates.seed(db, ids) { 1L }
        BuiltinTemplates.seed(db, ids) { 1L }
        val w = worlds.create("Arda")
        assertEquals(1, templates.forType(w, EntityType.CHARACTER).first().size, "no duplicates")

        val sheet = templates.forType(w, EntityType.CHARACTER).first().single()
        templates.softDelete(sheet.id)
        BuiltinTemplates.seed(db, ids) { 1L }
        assertTrue(templates.forType(w, EntityType.CHARACTER).first().isEmpty(), "deletion is respected")
    }
}
