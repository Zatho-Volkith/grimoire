package dev.grimoire.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val pill = RoundedCornerShape(50)

enum class ButtonKind { PRIMARY, SECONDARY, QUIET }

/** Pill button: Primary = solid accent, Secondary = muted fill, Quiet = text only. */
@Composable
fun GButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
    icon: IconSlot? = null,
    enabled: Boolean = true,
) {
    val (fill, ink) = when (kind) {
        ButtonKind.PRIMARY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        ButtonKind.SECONDARY -> MaterialTheme.grimoire.field to MaterialTheme.colorScheme.onSurface
        ButtonKind.QUIET -> Color.Transparent to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier.heightIn(min = 32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(fill, pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (kind == ButtonKind.QUIET) Space.sm else 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            icon?.let { GrimoireIcon(it, size = 16.dp) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
        }
    }
}

enum class ChipStyle { NORMAL, KEY }

/** Pill chip, optionally clickable and removable; [ChipStyle.KEY] is a keycap for shortcuts. */
@Composable
fun GChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    style: ChipStyle = ChipStyle.NORMAL,
) {
    val shape = if (style == ChipStyle.KEY) RoundedCornerShape(4.dp) else pill
    Row(
        modifier.background(MaterialTheme.grimoire.field, shape)
            .let { m -> onClick?.let { m.clickable(onClick = it) } ?: m }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.let { if (style == ChipStyle.KEY) it.copy(fontFamily = FontFamily.Monospace) else it },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        onRemove?.let {
            Text(
                "✕",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.grimoire.muted,
                modifier = Modifier.testTag("chip-remove").clickable(role = Role.Button, onClick = it),
            )
        }
    }
}
