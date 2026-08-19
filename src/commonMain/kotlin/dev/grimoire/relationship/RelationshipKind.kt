package dev.grimoire.relationship

/**
 * Preset vocabulary for `relationship.kind` with default display labels for each end. Not enforced by
 * the schema — `kind` is free text — these just prefill the add-relationship form. Labels are stored on
 * the row, so renaming a preset later never rewrites history.
 */
enum class RelationshipKind(val key: String, val label: String, val inverseLabel: String) {
    MEMBER_OF("member_of", "member of", "has member"),
    PARENT_OF("parent_of", "parent of", "child of"),
    SIBLING_OF("sibling_of", "sibling of", "sibling of"),
    PARTNER_OF("partner_of", "partner of", "partner of"),
    LOCATED_IN("located_in", "located in", "contains"),
    OWNS("owns", "owns", "owned by"),
    LEADS("leads", "leads", "led by"),
    ALLY_OF("ally_of", "ally of", "ally of"),
    RIVAL_OF("rival_of", "rival of", "rival of"),
    ENEMY_OF("enemy_of", "enemy of", "enemy of"),
    KNOWS("knows", "knows", "known by"),
    CUSTOM("custom", "", "");
}
