package dev.grimoire.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * App-wide keyboard shortcuts, dispatched from the window's preview pass so they win over
 * focused text fields. Returns true when the event was consumed.
 *
 * Ctrl/Cmd+S  save everything now        Ctrl/Cmd+1..4  Entities / Works / Timeline / Templates
 * Ctrl/Cmd+N  new item in the section    Ctrl/Cmd+F     search entities
 * Esc         close the open editor
 */
fun handleShortcut(session: Session, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val ws = session.workspace ?: return false
    val mod = event.isCtrlPressed || event.isMetaPressed

    when {
        mod && event.key == Key.S -> session.saveNow()
        mod && event.key == Key.N -> when (ws.section) {
            WorkspaceSection.ENTITIES -> ws.createEntity()
            WorkspaceSection.WORKS -> ws.createWork()
            WorkspaceSection.TIMELINE -> ws.createEvent()
            WorkspaceSection.TEMPLATES -> ws.createTemplate()
        }
        mod && event.key == Key.F -> ws.focusSearch()
        mod && event.key == Key.One -> ws.selectType(ws.type)
        mod && event.key == Key.Two -> ws.selectWorks()
        mod && event.key == Key.Three -> ws.selectTimeline()
        mod && event.key == Key.Four -> ws.selectTemplates()
        !mod && event.key == Key.Escape -> ws.closeCurrent()
        else -> return false
    }
    return true
}
