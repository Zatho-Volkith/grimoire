package dev.grimoire.update

import dev.grimoire.net.Fetcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A published release that is newer than the running build. */
data class Release(
    val version: String,
    val notes: String,
    val pageUrl: String,
    /** Windows installer asset, when the release ships one. */
    val msiUrl: String?,
)

/** Platform side of updating: getting from a [Release] to a running installer. */
interface UpdateLauncher {
    /** True when this platform can download and start the installer itself. */
    fun canInstall(release: Release): Boolean
    /** Downloads the installer and hands off to it; on success the app exits and this never returns. */
    suspend fun install(release: Release)
    /** Fallback for platforms that can't: the release page in the browser. */
    fun openReleasePage(release: Release)
}

/**
 * Asks GitHub for the latest release and compares it against the running version. Only ever
 * called by an explicit user action — the app never phones home on its own. Offline rule as
 * everywhere: a throw just means "couldn't reach GitHub".
 */
class UpdateChecker(
    private val fetch: Fetcher,
    val currentVersion: String,
    private val latestUrl: String = "https://api.github.com/repos/Zatho-Volkith/grimoire/releases/latest",
) {
    /** The latest release if it is strictly newer than [currentVersion], else null. */
    suspend fun check(): Release? {
        val release = Json.parseToJsonElement(fetch.getText(latestUrl)).jsonObject
        val version = release.getValue("tag_name").jsonPrimitive.content.removePrefix("v")
        if (!isNewer(version, currentVersion)) return null
        return Release(
            version = version,
            notes = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pageUrl = release.getValue("html_url").jsonPrimitive.content,
            msiUrl = release["assets"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it.getValue("name").jsonPrimitive.content.endsWith(".msi") }
                ?.getValue("browser_download_url")?.jsonPrimitive?.content,
        )
    }
}

/** True when semver [candidate] is strictly newer than [current]. Unparseable input is never newer. */
internal fun isNewer(candidate: String, current: String): Boolean {
    fun parse(v: String): List<Int>? =
        v.split(".").map { it.toIntOrNull() ?: return null }.takeIf { it.size == 3 }
    val a = parse(candidate) ?: return false
    val b = parse(current) ?: return false
    return a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x > y } ?: false
}
