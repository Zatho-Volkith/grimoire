package dev.grimoire

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InstanceLockTest {
    @Test
    fun `a second Grimoire on the same data directory is refused until the first lets go`() {
        val dir = createTempDirectory("grimoire-test")
        val first = assertNotNull(InstanceLock.acquire(dir))
        assertNull(InstanceLock.acquire(dir), "already held")

        first.channel().close()
        assertNotNull(InstanceLock.acquire(dir), "free again once released")
    }
}
