package dev.grimoire.id

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdGeneratorTest {

    private val uuidV7 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    private fun generator(clock: () -> Long) = IdGenerator(Random(42), clock)

    @Test
    fun `has uuid v7 shape, version and variant bits`() {
        val id = generator { 1_700_000_000_000 }.next()
        assertTrue(uuidV7.matches(id), "not a UUIDv7: $id")
    }

    @Test
    fun `timestamp is encoded in the first 48 bits`() {
        val ms = 0x0190_1234_5678L
        val id = generator { ms }.next()
        val encoded = id.replace("-", "").substring(0, 12).toLong(16)
        assertEquals(ms, encoded)
    }

    @Test
    fun `strictly monotonic within the same millisecond`() {
        val ids = generator { 1_700_000_000_000 }.let { g -> List(3_000) { g.next() } }
        assertStrictlyIncreasing(ids)
        assertEquals(ids.size, ids.toSet().size, "duplicates generated")
    }

    @Test
    fun `counter overflow borrows the next millisecond and stays monotonic`() {
        // 12-bit counter is seeded with at most 2^11, so >4096 ids in one ms must overflow.
        val ids = generator { 1_700_000_000_000 }.let { g -> List(10_000) { g.next() } }
        assertStrictlyIncreasing(ids)
    }

    @Test
    fun `clock going backwards does not break ordering`() {
        val ticks = longArrayOf(5_000, 4_000, 4_000, 3_000, 6_000).iterator()
        val g = generator { ticks.next() }
        val ids = List(5) { g.next() }
        assertStrictlyIncreasing(ids)
    }

    @Test
    fun `later milliseconds sort after earlier ones`() {
        var now = 1_700_000_000_000
        val g = generator { now }
        val a = g.next()
        now += 1
        val b = g.next()
        assertTrue(a < b)
    }

    private fun assertStrictlyIncreasing(ids: List<String>) {
        ids.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue(a < b, "id[$i]=$a is not < id[${i + 1}]=$b")
        }
    }
}
