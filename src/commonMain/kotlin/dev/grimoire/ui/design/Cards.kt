package dev.grimoire.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val cardShape = RoundedCornerShape(Radius.card)

/** Rounded card with a hairline border; optional serif title, muted caption and actions on the right. */
@Composable
fun GCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    caption: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.grimoire.card, cardShape)
            .border(1.dp, MaterialTheme.grimoire.border, cardShape)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (title != null || caption != null) CardHeader(title, caption, actions)
        content()
    }
}

@Composable
private fun CardHeader(title: String?, caption: String?, actions: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.grimoire.muted) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A number with a caps label, for counts and summaries. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.grimoire.card, cardShape)
            .border(1.dp, MaterialTheme.grimoire.border, cardShape)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.bodyMedium.fontFamily))
        SectionLabel(label)
    }
}

/** A dialog that looks like a card: header, content, actions bottom-right. */
@Composable
fun GDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    properties: DialogProperties = DialogProperties(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = properties) {
        Column(
            modifier.widthIn(min = 360.dp, max = 900.dp)
                .background(MaterialTheme.grimoire.card, cardShape)
                .border(1.dp, MaterialTheme.grimoire.border, cardShape)
                .padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            CardHeader(title, caption) {}
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End), content = actions)
        }
    }
}
