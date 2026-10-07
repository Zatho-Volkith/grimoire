package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.grimoire.work.Markdown
import dev.grimoire.work.TextChat
import dev.grimoire.work.TextItem
import dev.grimoire.work.TextsPalette

/**
 * A `::: texts` conversation drawn the way the AO3 Work Skin ([dev.grimoire.work.Ao3WorkSkin])
 * draws it — same palette, same name rules — so the preview matches the posted chapter.
 */
@Composable
fun TextChatView(chat: TextChat, modifier: Modifier = Modifier) {
    val meta = Color(TextsPalette.META_TEXT)
    Column(
        modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .background(Color(TextsPalette.CHAT_BG), RoundedCornerShape(20.dp))
            .border(1.dp, Color(TextsPalette.BORDER), RoundedCornerShape(20.dp))
            .padding(12.dp)
            .testTag("texts-chat"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (chat.title.isNotBlank()) {
            Text(
                chat.title, fontWeight = FontWeight.Bold, color = Color(TextsPalette.RECEIVED_TEXT),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(TextsPalette.BORDER))
        }
        chat.items.forEach { item ->
            when (item) {
                is TextItem.Message -> Bubble(item, sent = item.sender != null && item.sender == chat.owner, showName = chat.isGroup)
                is TextItem.Receipt -> Text(
                    item.text, style = MaterialTheme.typography.labelSmall, color = meta, textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().testTag("texts-receipt"),
                )
                is TextItem.Notice -> Text(
                    item.text, style = MaterialTheme.typography.labelMedium, fontStyle = FontStyle.Italic, color = meta,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("texts-notice"),
                )
            }
        }
    }
}

@Composable
private fun Bubble(message: TextItem.Message, sent: Boolean, showName: Boolean) {
    val bg = Color(if (sent) TextsPalette.SENT_BG else TextsPalette.RECEIVED_BG)
    val fg = Color(if (sent) TextsPalette.SENT_TEXT else TextsPalette.RECEIVED_TEXT)
    val text = buildAnnotatedString {
        message.text.split('\n').forEachIndexed { i, line ->
            if (i > 0) append("\n")
            append(annotate(Markdown.parseInline(line), fg))
        }
    }
    MaxFraction(0.75f, Modifier.fillMaxWidth(), if (sent) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .background(bg, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag(if (sent) "texts-me" else "texts-them"),
        ) {
            if (showName && !sent && message.sender != null) {
                Text(
                    message.sender, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = Color(TextsPalette.META_TEXT),
                )
            }
            Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Lays out [content] at its natural width, capped at [fraction] of the row, aligned [align]. */
@Composable
private fun MaxFraction(fraction: Float, modifier: Modifier, align: Alignment.Horizontal, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val cap = (constraints.maxWidth * fraction).toInt()
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, maxWidth = cap))
        layout(constraints.maxWidth, placeable.height) {
            placeable.place(align.align(placeable.width, constraints.maxWidth, layoutDirection), 0)
        }
    }
}
