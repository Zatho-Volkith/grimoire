package dev.grimoire

import dev.grimoire.update.Release
import dev.grimoire.update.UpdateLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * Windows: download the MSI and hand off to msiexec — the stable upgradeUuid makes that an
 * in-place upgrade, no uninstall. Everywhere else: the release page in the browser
 * (deb/rpm/AppImage installs are the package manager's job, not ours).
 */
class DesktopUpdateLauncher : UpdateLauncher {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    override fun canInstall(release: Release): Boolean =
        release.msiUrl != null && "win" in System.getProperty("os.name").lowercase()

    override suspend fun install(release: Release) {
        val url = checkNotNull(release.msiUrl)
        val target = Files.createTempDirectory("grimoire-update").resolve("grimoire-${release.version}.msi")
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(target))
            check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} downloading $url" }
            ProcessBuilder("msiexec", "/i", target.toString()).start()
        }
        // The installer can't replace files that are still running; callers flushed before us.
        exitProcess(0)
    }

    override fun openReleasePage(release: Release) {
        Desktop.getDesktop().browse(URI.create(release.pageUrl))
    }
}
