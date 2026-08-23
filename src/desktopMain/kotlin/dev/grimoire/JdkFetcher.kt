package dev.grimoire

import dev.grimoire.net.Fetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The real [Fetcher]: JDK built-in client, no new dependencies. Polite UA, honest timeouts. */
class JdkFetcher : Fetcher {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Grimoire (local-first worldbuilding app)")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} for $url" }
        response.body()
    }
}
