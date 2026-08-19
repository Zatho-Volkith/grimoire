package dev.grimoire.entity

/** The `entity.type` vocabulary. [key] is the stored string. */
enum class EntityType(val key: String, val label: String, val plural: String) {
    CHARACTER("character", "Character", "Characters"),
    LOCATION("location", "Location", "Locations"),
    FACTION("faction", "Faction", "Factions"),
    ITEM("item", "Item", "Items"),
    CONCEPT("concept", "Concept", "Concepts"),
    SYSTEM("system", "System", "Systems");

    companion object {
        fun fromKey(key: String): EntityType = entries.first { it.key == key }
    }
}
