package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The last background failure (a save, an export, …), shown as a dismissible banner. Fed by the
 * app scope's exception handler, so nothing that goes wrong off-screen fails silently.
 */
class Problems {
    var message by mutableStateOf<String?>(null); private set

    fun report(t: Throwable) {
        message = "Something went wrong: " + (t.message?.take(200) ?: t::class.simpleName ?: "unknown error")
    }

    fun dismiss() { message = null }
}
