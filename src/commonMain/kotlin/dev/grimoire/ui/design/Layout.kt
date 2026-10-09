package dev.grimoire.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val rowShape = RoundedCornerShape(Radius.field)

/** Serif page title (tag `page-title`), optional muted subtitle, pill actions on the right, hairline below. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) = PageHeader(
    titleField = {
        Text(
            title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("page-title"),
        )
    },
    modifier = modifier, subtitle = subtitle, actions = actions,
)

/** Header whose title is caller-supplied — e.g. an inline-editable entity name. */
@Composable
fun PageHeader(
    titleField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                titleField()
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HorizontalDivider(color = MaterialTheme.grimoire.border)
    }
}

/** Small letter-spaced capitals: "FIELDS", "WORLD". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.grimoire.muted, modifier = modifier)
}

/** Sidebar row: icon + label; selected rows get the accent tint and a bar on the left. */
@Composable
fun NavRow(label: String, icon: IconSlot?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().height(36.dp)
            .background(if (selected) MaterialTheme.grimoire.selected else Color.Transparent, rowShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 8.dp)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp)))
        Row(Modifier.padding(horizontal = Space.sm), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides ink) {
                icon?.let { GrimoireIcon(it) }
                Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** List item: title + muted secondary line, hover-free selected tint, optional trailing content. */
@Composable
fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    selected: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.grimoire.selected else Color.Transparent, rowShape)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            secondary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}
