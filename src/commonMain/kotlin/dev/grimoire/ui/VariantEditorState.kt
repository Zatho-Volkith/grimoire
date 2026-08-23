package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.variant.VariantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editing session for one variant's overrides. Blank fields mean "inherit canon" and are
 * stored as NULL. Same debounce/flush contract as every other content editor.
 */
class VariantEditorState(
    val id: String,
    private val repo: VariantRepository,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var scopeLabel: String = ""; private set
    var nameOverride by mutableStateOf(""); private set
    var summaryOverride by mutableStateOf(""); private set
    var bodyOverride by mutableStateOf(""); private set
    var dirty by mutableStateOf(false); private set

    private var saveJob: Job? = null

    suspend fun load() {
        val v = repo.variant(id).first() ?: return
        scopeLabel = v.work_title?.let { "In “$it”" } ?: "AU: ${v.continuity_key}"
        nameOverride = v.name_override.orEmpty()
        summaryOverride = v.summary_override.orEmpty()
        bodyOverride = v.body_override.orEmpty()
        loaded = true
    }

    fun editNameOverride(v: String) = edit { nameOverride = v }
    fun editSummaryOverride(v: String) = edit { summaryOverride = v }
    fun editBodyOverride(v: String) = edit { bodyOverride = v }

    suspend fun flush() {
        saveJob?.cancelAndJoin()
        save()
    }

    private fun edit(change: () -> Unit) {
        change()
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch { delay(EntityEditorState.SAVE_DELAY_MS); save() }
    }

    private suspend fun save() {
        if (!dirty) return
        dirty = false
        withContext(NonCancellable) {
            repo.updateContent(
                id = id,
                nameOverride = nameOverride.ifBlank { null },
                summaryOverride = summaryOverride.ifBlank { null },
                bodyOverride = bodyOverride.ifBlank { null },
            )
        }
    }
}
