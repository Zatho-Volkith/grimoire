package dev.grimoire.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

private val fieldShape = RoundedCornerShape(Radius.field)

/** Filled field. The label sits above; while empty, `placeholder ?: label` shows inside. */
@Composable
fun GField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    minLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    FieldLabel(modifier, label) {
        BasicTextField(
            value, onValueChange,
            modifier = fieldModifier(onPreviewKeyEvent),
            singleLine = singleLine, readOnly = readOnly, minLines = if (singleLine) 1 else minLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interaction,
            decorationBox = { field -> FieldDecoration(value.isEmpty(), placeholder ?: label, interaction, trailing, field) },
        )
    }
}

/** Selection-aware variant (the chapter body tracks its cursor). */
@Composable
fun GField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    minLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    FieldLabel(modifier, label) {
        BasicTextField(
            value, onValueChange,
            modifier = fieldModifier(onPreviewKeyEvent),
            singleLine = singleLine, readOnly = readOnly, minLines = if (singleLine) 1 else minLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interaction,
            decorationBox = { field -> FieldDecoration(value.text.isEmpty(), placeholder ?: label, interaction, trailing, field) },
        )
    }
}

/** Multi-line [GField]. */
@Composable
fun GTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    minLines: Int = 3,
) = GField(value, onValueChange, modifier, label, placeholder, singleLine = false, minLines = minLines)

// Merging descendants puts the in-field hint into the editable node's semantics, so tests (and screen
// readers) see an empty field by its label, as they did with Material's floating label.
private fun fieldModifier(onKey: ((KeyEvent) -> Boolean)?): Modifier =
    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
        .let { m -> onKey?.let { m.onPreviewKeyEvent(it) } ?: m }

@Composable
private fun FieldLabel(modifier: Modifier, label: String?, field: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.grimoire.muted) }
        field()
    }
}

@Composable
private fun FieldDecoration(
    empty: Boolean,
    hint: String?,
    interaction: MutableInteractionSource,
    trailing: (@Composable () -> Unit)?,
    field: @Composable () -> Unit,
) {
    val focused by interaction.collectIsFocusedAsState()
    val edge = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.grimoire.field
    Row(
        Modifier.background(MaterialTheme.grimoire.field, fieldShape).border(1.dp, edge, fieldShape)
            .padding(horizontal = Space.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (empty && hint != null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            }
            field()
        }
        trailing?.invoke()
    }
}
