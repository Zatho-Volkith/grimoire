package dev.grimoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.grimoire.work.TextChat

/**
 * Builds a `::: texts` conversation row by row with a live bubble preview. Enter in a message
 * starts the next one (sender prefilled to whoever replies); Shift+Enter breaks the line.
 */
@Composable
fun TextConvoDialog(state: TextConvoState, onSave: () -> Unit, onCancel: () -> Unit) {
    var focusKey by remember { mutableStateOf<Int?>(null) }

    // A writing surface: only Cancel/Save close it, never a stray click or Escape.
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(Modifier.width(900.dp).height(600.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Text conversation", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        state.title, state::editTitle, label = { Text("Chat name") }, singleLine = true,
                        modifier = Modifier.weight(1f).testTag("convo-title"),
                    )
                    NameField(state.owner, state::editOwner, "Phone owner", state.senderSuggestions, Modifier.weight(1f), "convo-owner")
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.rows.forEachIndexed { index, row ->
                            key(row.key) {
                                ConvoRowEditor(state, row, index, focus = focusKey == row.key) { focusKey = it }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { focusKey = state.addMessage() }) { Text("+ Message") }
                            TextButton(onClick = { focusKey = state.addReceipt() }) { Text("+ Receipt") }
                            TextButton(onClick = { focusKey = state.addNotice() }) { Text("+ Notice") }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        TextChatView(state.toChat() ?: TextChat(state.title, null, emptyList()))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onSave) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun ConvoRowEditor(state: TextConvoState, row: ConvoRow, index: Int, focus: Boolean, onFocusRow: (Int) -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) requester.requestFocus() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (row.kind) {
            RowKind.MESSAGE -> NameField(
                row.sender, { state.editSender(row.key, it) }, "Sender", state.senderSuggestions,
                Modifier.width(150.dp), "convo-sender-$index",
            )
            RowKind.RECEIPT -> KindLabel("Receipt")
            RowKind.NOTICE -> KindLabel("Notice")
        }
        OutlinedTextField(
            row.text, { state.editText(row.key, it) },
            singleLine = row.kind != RowKind.MESSAGE,
            placeholder = { Text(if (row.kind == RowKind.RECEIPT) "Read 2:15 AM" else if (row.kind == RowKind.NOTICE) "Alex added Jordan" else "Message") },
            modifier = Modifier.weight(1f).focusRequester(requester).testTag("convo-text-$index").onPreviewKeyEvent { e ->
                val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
                if (row.kind == RowKind.MESSAGE && enter && !e.isShiftPressed) {
                    if (e.type == KeyEventType.KeyDown) onFocusRow(state.addMessage(afterKey = row.key))
                    true
                } else false
            },
        )
        TextButton(onClick = { state.move(row.key, -1) }) { Text("↑") }
        TextButton(onClick = { state.move(row.key, 1) }) { Text("↓") }
        TextButton(onClick = { state.remove(row.key) }) { Text("✕", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun KindLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.width(150.dp).padding(start = 12.dp),
    )
}

/** Free-text name with a ▾ menu of names already in play. */
@Composable
private fun NameField(value: String, onChange: (String) -> Unit, label: String, suggestions: List<String>, modifier: Modifier, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value, onChange, label = { Text(label) }, singleLine = true,
            trailingIcon = if (suggestions.isEmpty()) null else ({ TextButton(onClick = { open = true }) { Text("▾") } }),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            suggestions.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onChange(name); open = false })
            }
        }
    }
}
