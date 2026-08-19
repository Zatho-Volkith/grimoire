package dev.grimoire

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.grimoire.db.GrimoireDatabase
import dev.grimoire.entity.EntityRepository
import dev.grimoire.entity.EntityType
import dev.grimoire.id.IdGenerator
import dev.grimoire.relationship.RelationshipRepository
import dev.grimoire.ui.App
import dev.grimoire.ui.Session
import dev.grimoire.world.WorldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.assertEquals

/** End-to-end through the real Compose tree and a real SQLite file. */
@OptIn(ExperimentalTestApi::class)
class WorkspaceScreenTest {
    @get:Rule val rule = createComposeRule()

    private val db = GrimoireDatabase(DriverFactory.create(createTempDirectory("grimoire-ui").resolve("g.db")))
    private val ids = IdGenerator(Random(3), System::currentTimeMillis)
    private val worlds = WorldRepository(db, ids, System::currentTimeMillis)
    private val entities = EntityRepository(db, ids, System::currentTimeMillis)
    private val relationships = RelationshipRepository(db, ids, System::currentTimeMillis)
    private val session = Session(worlds, entities, relationships, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Test
    fun `create world, add a location, rename it, find it by search`() {
        rule.setContent { App(session) }

        rule.onNode(hasSetTextAction() and hasText("New world")).performTextInput("Arda")
        rule.onNodeWithText("Create").performClick()
        rule.waitUntil(5_000) { session.workspace != null }
        rule.waitUntilAtLeastOneExists(hasText("Arda"), 5_000)

        rule.onNodeWithText("Locations").performClick()
        rule.onNodeWithText("New Location").performClick()
        rule.waitUntil(5_000) { session.workspace?.editor?.loaded == true }
        rule.waitUntilAtLeastOneExists(hasText("New Location") and hasSetTextAction(), 5_000)

        val nameField = rule.onNode(hasSetTextAction() and hasText("Name"))
        nameField.performTextClearance()
        nameField.performTextInput("Rivertown")
        runBlocking { session.flush() }

        val ws = session.workspace!!
        val names = runBlocking { entities.byType(ws.worldId, EntityType.LOCATION).first() }.map { it.name }
        assertEquals(listOf("Rivertown"), names)

        rule.onNode(hasSetTextAction() and hasText("Search all entities")).performTextInput("river")
        rule.waitUntilAtLeastOneExists(hasText("1 result(s)"), 5_000)
        rule.onNodeWithText("Location").assertIsDisplayed() // type shown as subtitle in search results
    }

    @Test
    fun `relationship added through the panel appears on both entities`() {
        val w = runBlocking { worlds.create("Arda") }
        val kaede = runBlocking { entities.create(w, EntityType.CHARACTER, "Kaede") }
        val rin = runBlocking { entities.create(w, EntityType.CHARACTER, "Rin") }
        session.openWorld(w)
        rule.setContent { App(session) }
        session.workspace!!.open(kaede)
        rule.waitUntilAtLeastOneExists(hasText("None yet."), 5_000)

        // Default kind is `knows`; pick Rin from the target search, add.
        rule.onNode(hasSetTextAction() and hasText("Find target entity")).performTextInput("ri")
        rule.waitUntilAtLeastOneExists(hasText("Rin") and hasText("Character") and hasClickAction(), 5_000)
        rule.onNode(hasText("Rin") and hasText("Character") and hasClickAction()).performClick()
        rule.onNodeWithText("Add").performClick()
        rule.waitUntilAtLeastOneExists(hasText("knows"), 5_000)

        val fromRin = runBlocking { relationships.forEntity(rin).first() }.single()
        assertEquals("known by", fromRin.shown_label)
        assertEquals("Kaede", fromRin.other_name)

        // The other end is a link: clicking it opens that entity.
        rule.onNode(hasTestTag("relationship-link") and hasText("Rin")).performClick()
        rule.waitUntil(5_000) { session.workspace?.editor?.id == rin }
        rule.waitUntilAtLeastOneExists(hasText("known by"), 5_000)
    }
}
