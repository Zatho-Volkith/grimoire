package dev.grimoire.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtsQueryTest {
    @Test fun `blank input yields no query`() {
        assertNull(ftsQuery(""))
        assertNull(ftsQuery("   \t "))
    }

    @Test fun `tokens become quoted prefix terms`() {
        assertEquals("\"kae\"* \"nor\"*", ftsQuery("  kae   nor "))
    }

    @Test fun `fts syntax and quotes are neutralised`() {
        assertEquals("\"NEAR\"* \"a\"\"b\"* \"name:x\"*", ftsQuery("NEAR a\"b name:x"))
    }
}
