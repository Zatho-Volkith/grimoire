package dev.grimoire.ui

import dev.grimoire.ui.design.ButtonKind
import dev.grimoire.ui.design.GButton
import dev.grimoire.ui.design.GDialog
import dev.grimoire.ui.design.GField
import dev.grimoire.ui.design.grimoire
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    GDialog(
        "Text conversation", onDismiss = onCancel, modifier = Modifier.width(900.dp).height(640.dp),
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        actions = {
            GButton("Cancel", onCancel, kind = ButtonKind.QUIET)
            GButton("Save", onSave, kind = ButtonKind.PRIMARY)
        },
    ) {
            run {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GField(
                        state.title, state::editTitle, Modifier.weight(1f), label = "Chat name",
                        fieldModifier = Modifier.testTag("convo-title"),
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
                            GButton("+ Message", { focusKey = state.addMessage() }, kind = ButtonKind.QUIET)
                            GButton("+ Receipt", { focusKey = state.addReceipt() }, kind = ButtonKind.QUIET)
                            GButton("+ Notice", { focusKey = state.addNotice() }, kind = ButtonKind.QUIET)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        TextChatView(state.toChat() ?: TextChat(state.title, null, emptyList()))
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
        GField(
            row.text, { state.editText(row.key, it) }, Modifier.weight(1f),
            singleLine = row.kind != RowKind.MESSAGE,
            placeholder = if (row.kind == RowKind.RECEIPT) "Read 2:15 AM" else if (row.kind == RowKind.NOTICE) "Alex added Jordan" else "Message",
            fieldModifier = Modifier.focusRequester(requester).testTag("convo-text-$index"),
            onPreviewKeyEvent = { e ->
                val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
                if (row.kind == RowKind.MESSAGE && enter && !e.isShiftPressed) {
                    if (e.type == KeyEventType.KeyDown) onFocusRow(state.addMessage(afterKey = row.key))
                    true
                } else false
            },
        )
        GButton("↑", { state.move(row.key, -1) }, kind = ButtonKind.QUIET)
        GButton("↓", { state.move(row.key, 1) }, kind = ButtonKind.QUIET)
        GButton("✕", { state.remove(row.key) }, kind = ButtonKind.QUIET)
    }
}

@Composable
private fun KindLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.grimoire.muted,
        modifier = Modifier.width(150.dp).padding(start = 12.dp),
    )
}

/** Free-text name with a ▾ menu of names already in play. */
@Composable
private fun NameField(value: String, onChange: (String) -> Unit, label: String, suggestions: List<String>, modifier: Modifier, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GField(
            value, onChange, Modifier.fillMaxWidth(), placeholder = label,
            trailing = if (suggestions.isEmpty()) null else ({ GButton("▾", { open = true }, kind = ButtonKind.QUIET) }),
            fieldModifier = Modifier.testTag(tag),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            suggestions.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onChange(name); open = false })
            }
        }
    }
}
