package dev.grimoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.grimoire.work.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.grimoire.work.MdBlock
import dev.grimoire.work.MdRun

/**
 * Live-rendered view of chapter Markdown, driven by [Markdown.parse] — the same model the
 * AO3 HTML export uses, so the preview always matches what AO3 will show.
 */
@Composable
fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    // First composition parses synchronously (opening a chapter); every edit after that
    // re-parses on a background dispatcher, latest-wins, so typing in a long chapter
    // never pays the parse on the UI thread.
    val blocks by produceState(initialValue = remember { Markdown.parse(markdown) }, markdown) {
        value = withContext(Dispatchers.Default) { Markdown.parse(markdown) }
    }
    Column(
        modifier.verticalScroll(rememberScrollState()).testTag("md-preview"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { BlockView(it) }
    }
}

@Composable
private fun BlockView(block: MdBlock) {
    val linkColor = MaterialTheme.colorScheme.primary
    when (block) {
        is MdBlock.Heading -> Text(
            annotate(block.text, linkColor),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.headlineSmall
                3 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
        )

        is MdBlock.Paragraph -> Text(
            buildAnnotatedString {
                block.lines.forEachIndexed { i, line ->
                    if (i > 0) append("\n")
                    append(annotate(line, linkColor))
                }
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        MdBlock.Hr -> HorizontalDivider(Modifier.padding(vertical = 4.dp).testTag("md-hr"))

        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                block.blocks.forEach { BlockView(it) }
            }
        }

        is MdBlock.Listing -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEachIndexed { i, item ->
                Row {
                    Text(if (block.ordered) "${i + 1}. " else "•  ", style = MaterialTheme.typography.bodyLarge)
                    Text(annotate(item, linkColor), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun annotate(runs: List<MdRun>, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        runs.forEach { r ->
            withStyle(
                SpanStyle(
                    fontWeight = if (r.bold) FontWeight.Bold else null,
                    fontStyle = if (r.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        r.strike -> TextDecoration.LineThrough
                        r.link != null -> TextDecoration.Underline
                        else -> null
                    },
                    color = if (r.link != null) linkColor else Color.Unspecified,
                ),
            ) { append(r.text) }
        }
    }
