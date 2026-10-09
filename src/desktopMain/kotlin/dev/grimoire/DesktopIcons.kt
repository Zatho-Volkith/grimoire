package dev.grimoire

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import dev.grimoire.ui.design.IconLoader
import dev.grimoire.ui.design.IconSlot

/** Loads `icons/<slot>.svg` from resources; absent or unreadable files fall back to the placeholder. */
class DesktopIcons(private val resourcePrefix: String = "icons/") : IconLoader {
    private val cache = HashMap<IconSlot, Painter?>()

    override fun load(slot: IconSlot): Painter? = cache.getOrPut(slot) {
        runCatching {
            javaClass.classLoader.getResourceAsStream(resourcePrefix + slot.fileName)
                ?.use { loadSvgPainter(it, Density(1f)) }
        }.getOrNull()
    }
}
