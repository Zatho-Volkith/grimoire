package dev.grimoire.id

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random

/**
 * Generates UUIDv7 strings (RFC 9562) that are strictly monotonic per generator instance,
 * including within a single millisecond.
 *
 * Layout: 48-bit unix millis | 4-bit version (7) | 12-bit counter | 2-bit variant (10) | 62 random bits.
 *
 * The 12-bit `rand_a` field is used as a per-millisecond counter (RFC 9562 §6.2, method 1).
 * It is seeded with 11 random bits each new millisecond, leaving headroom for 2048+ ids
 * before overflow; on overflow (or if the wall clock stalls / goes backwards) the generator
 * borrows the next millisecond so ordering is never violated.
 *
 * @param random source of the random bits; on the JVM pass a SecureRandom-backed instance.
 * @param clock  current time in epoch millis.
 */
@OptIn(ExperimentalAtomicApi::class)
class IdGenerator(
    private val random: Random,
    private val clock: () -> Long,
) {
    /** Packed `(lastMillis shl 12) or counter` — updated with CAS so concurrent callers stay monotonic. */
    private val state = AtomicLong(0L)

    fun next(): String {
        while (true) {
            val prev = state.load()
            val prevMs = prev ushr 12
            val prevCounter = prev and 0xFFF
            val nowMs = clock()

            val ms: Long
            val counter: Long
            when {
                nowMs > prevMs -> { ms = nowMs; counter = random.nextLong(0L, 1L shl 11) }
                prevCounter < 0xFFF -> { ms = prevMs; counter = prevCounter + 1 }
                else -> { ms = prevMs + 1; counter = 0L }
            }

            val next = (ms shl 12) or counter
            if (state.compareAndSet(prev, next)) return format(ms, counter, random.nextLong())
        }
    }

    private fun format(ms: Long, counter: Long, rand: Long): String {
        val hi = (ms shl 16) or (0x7L shl 12) or counter
        val lo = (rand and 0x3FFF_FFFF_FFFF_FFFFL) or (1L shl 63)
        val hex = hi.hex16() + lo.hex16()
        return buildString(36) {
            append(hex, 0, 8); append('-')
            append(hex, 8, 12); append('-')
            append(hex, 12, 16); append('-')
            append(hex, 16, 20); append('-')
            append(hex, 20, 32)
        }
    }

    private fun Long.hex16(): String = toULong().toString(16).padStart(16, '0')
}
