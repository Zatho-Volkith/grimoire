package dev.grimoire.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every icon the UI uses. Artwork goes in resources as `icons/<fileName>`; see icons/README.md. */
enum class IconSlot {
    CHARACTER, LOCATION, FACTION, ITEM, CONCEPT, SYSTEM, WORKS, TIMELINE, TEMPLATES, TRASH,
    SEARCH, ADD, CLOSE, EXPORT, SETTINGS, BACK;

    val fileName: String get() = name.lowercase() + ".svg"
}

/** Supplies artwork for a slot, or null to draw the placeholder. Desktop loads SVGs from resources. */
fun interface IconLoader {
    fun load(slot: IconSlot): Painter?
}

val LocalIconLoader: ProvidableCompositionLocal<IconLoader> = staticCompositionLocalOf { IconLoader { null } }

/** The slot's artwork tinted to [tint], or — until artwork exists — its initial in a soft rounded square. */
@Composable
fun GrimoireIcon(
    slot: IconSlot,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = LocalContentColor.current,
) {
    val loader = LocalIconLoader.current
    val painter = remember(loader, slot) { loader.load(slot) }
    val tagged = modifier.size(size).testTag("icon-" + slot.name.lowercase())
    if (painter != null) {
        Icon(painter, contentDescription = null, modifier = tagged, tint = tint)
    } else {
        Box(tagged, contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(size).background(MaterialTheme.grimoire.selected, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    slot.name.first().toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = (size.value * 0.55f).sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("icon-placeholder"),
                )
            }
        }
    }
}
