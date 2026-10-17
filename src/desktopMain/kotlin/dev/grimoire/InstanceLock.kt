package dev.grimoire

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.createDirectories

/**
 * One running Grimoire per data directory. Two copies on one database would each hold their own
 * copy of an open entity and silently overwrite each other's saves. The OS drops the lock when
 * the process exits, crash included, so a stale lock file never blocks a restart.
 */
object InstanceLock {
    /** Returns the held lock, or null if another Grimoire already holds it. Keep the result reachable. */
    fun acquire(dir: Path): FileLock? {
        dir.createDirectories()
        val channel = FileChannel.open(dir.resolve("grimoire.lock"), CREATE, WRITE)
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null // held by this same JVM
        }
        if (lock == null) channel.close()
        return lock
    }
}
