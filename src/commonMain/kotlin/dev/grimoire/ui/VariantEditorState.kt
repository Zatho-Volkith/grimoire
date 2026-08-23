package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.json.JsonStringMap
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
    /** The parent entity's attributes when this editor opened — placeholders and key list. */
    val parentAttributes: Map<String, String>,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var scopeLabel: String = ""; private set
    var nameOverride by mutableStateOf(""); private set
    var summaryOverride by mutableStateOf(""); private set
    var bodyOverride by mutableStateOf(""); private set
    /** Only the changed keys — everything absent inherits the entity's own value. */
    var patch by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var dirty by mutableStateOf(false); private set

    /** Keys worth showing: the parent's attributes plus any stale patch keys still stored. */
    val patchKeys: List<String> get() = (parentAttributes.keys + patch.keys).distinct()

    private var saveJob: Job? = null

    suspend fun load() {
        val v = repo.variant(id).first() ?: return
        scopeLabel = v.work_title?.let { "In “$it”" } ?: "AU: ${v.continuity_key}"
        nameOverride = v.name_override.orEmpty()
        summaryOverride = v.summary_override.orEmpty()
        bodyOverride = v.body_override.orEmpty()
        patch = JsonStringMap.decode(v.attribute_patch)
        loaded = true
    }

    fun editNameOverride(v: String) = edit { nameOverride = v }
    fun editSummaryOverride(v: String) = edit { summaryOverride = v }
    fun editBodyOverride(v: String) = edit { bodyOverride = v }

    /** Blank clears the override so the entity's own value shows through again. */
    fun editPatch(key: String, v: String) = edit {
        patch = if (v.isBlank()) patch - key else patch + (key to v)
    }

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
                attributePatch = patch,
            )
        }
    }
}
