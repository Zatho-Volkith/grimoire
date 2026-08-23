package dev.grimoire.net

/**
 * Minimal HTTP GET boundary, same pattern as ExportSink/SettingsStore: commonMain code and
 * tests see only this; the JDK-client implementation lives in desktopMain. Offline-first rule:
 * callers treat any throw as "we're offline" and degrade — nothing blocks on it.
 */
fun interface Fetcher {
    /** Returns the response body; throws on network failure or a non-2xx status. */
    suspend fun getText(url: String): String
}

/** Percent-encodes a URL query value (UTF-8). Tiny, so commonMain needs no java.net. */
fun urlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        when {
            c.isLetterOrDigit() || c in "-._~" -> append(c)
            else -> append('%').append(((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
        }
    }
}
