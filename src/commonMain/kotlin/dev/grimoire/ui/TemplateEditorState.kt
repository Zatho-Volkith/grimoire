package dev.grimoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grimoire.entity.EntityType
import dev.grimoire.template.TemplateField
import dev.grimoire.template.TemplateFields
import dev.grimoire.template.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editing session for one template: its name and its ordered field list, saved together
 * under the usual debounce+flush contract. Field keys are minted once from the label at
 * add time and never change, so renaming a label can't orphan entity values.
 */
class TemplateEditorState(
    val id: String,
    private val repo: TemplateRepository,
    private val scope: CoroutineScope,
) {
    var loaded by mutableStateOf(false); private set
    var entityType: EntityType = EntityType.CHARACTER; private set
    var name by mutableStateOf(""); private set
    var fields by mutableStateOf<List<TemplateField>>(emptyList()); private set
    var dirty by mutableStateOf(false); private set

    private var saveJob: Job? = null

    suspend fun load() {
        val t = repo.template(id).first() ?: return
        entityType = EntityType.fromKey(t.entity_type)
        name = t.name
        fields = TemplateFields.decode(t.fields)
        loaded = true
    }

    fun editName(v: String) = edit { name = v }

    fun addField(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        edit { fields = fields + TemplateField(TemplateFields.keyFor(trimmed, fields.map { it.key }.toSet()), trimmed) }
    }

    fun renameField(key: String, label: String) =
        edit { fields = fields.map { if (it.key == key) it.copy(label = label) else it } }

    fun removeField(key: String) = edit { fields = fields.filterNot { it.key == key } }

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
            repo.update(id, name.trim().ifEmpty { "Untitled template" }, fields)
        }
    }
}
