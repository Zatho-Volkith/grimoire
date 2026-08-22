package dev.grimoire.settings

/**
 * App-level preferences (theme, …) as a flat string map. Deliberately not in the database:
 * these belong to this machine, not to any world, and must never ride along in a sync.
 */
interface SettingsStore {
    fun load(): Map<String, String>
    fun save(values: Map<String, String>)
}
