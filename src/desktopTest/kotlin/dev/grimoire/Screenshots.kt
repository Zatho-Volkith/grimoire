package dev.grimoire

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.grimoire.entity.EntityType
import dev.grimoire.settings.SettingsStore
import dev.grimoire.ui.App
import dev.grimoire.ui.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the main screens in every theme to `build/screenshots/<theme>/<screen>.png` for visual review.
 * On demand only: `./gradlew screenshots` sets `grimoire.screenshots=true`; a normal `desktopTest` skips it.
 */
@OptIn(ExperimentalTestApi::class)
class Screenshots {
    private val settings = object : SettingsStore {
        override fun load() = emptyMap<String, String>()
        override fun save(values: Map<String, String>) = Unit
    }

    @Test
    fun `render every screen in every theme`() {
        if (System.getProperty("grimoire.screenshots") != "true") return
        AppTheme.entries.forEach { theme -> renderTheme(theme) }
    }

    private fun renderTheme(theme: AppTheme) = runDesktopComposeUiTest(width = 1600, height = 1000) {
        val out = File("build/screenshots/${theme.key}").apply { mkdirs() }
        fun shot(name: String) {
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
        fun await(what: String, condition: () -> Boolean) = waitUntil(what, 10_000, condition)

        val session = testSession(settings)
        session.selectTheme(theme)
        setContent { App(session, DesktopFonts.load(), DesktopIcons()) }
        shot("picker")

        session.createTutorialWorld()
        await("tutorial world opens") { session.workspace != null }
        val ws = session.workspace!!
        shot("shell")

        val mira = runBlocking { session.entities.byType(ws.worldId, EntityType.CHARACTER).first() }
            .first { it.name == "Mira Vale" }
        ws.open(mira.id)
        await("entity editor loads") { ws.editor?.loaded == true }
        shot("entity")

        val sheet = runBlocking { session.templates.byBuiltinKey("character") }!!.id
        ws.selectTemplates()
        ws.openTemplate(sheet)
        await("template editor loads") { ws.templateEditor?.loaded == true }
        waitUntil("field rows render", 10_000) { onAllNodes(hasTestTag("field-row-race")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("field-row-race").performScrollTo().performClick()
        shot("template")

        val work = runBlocking { session.works.forWorld(ws.worldId).first() }.first()
        val chapters = runBlocking { session.works.chapters(work.id).first() }
        ws.openWork(work.id, chapters.getOrElse(1) { chapters.first() }.id)
        await("chapter editor loads") { ws.workEditor?.chapterEditor?.loaded == true }
        shot("chapter")

        ws.selectTimeline()
        if (!ws.timelineView) ws.toggleTimelineView()
        shot("timeline")
    }
}
