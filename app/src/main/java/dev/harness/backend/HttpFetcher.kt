package dev.harness.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface HttpFetcher {
    /** GET [url] and return the status line plus the start of the body as text. */
    suspend fun get(url: String): String
}

/** OkHttp GET; the body is cut at [maxBytes] so a large download cannot fill memory. */
class OkHttpFetcher(
    private val client: OkHttpClient = OkHttpClient(),
    private val maxBytes: Long = 64 * 1024,
) : HttpFetcher {

    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "Harness/0.1 (Android agent)").build()
        client.newCall(request).execute().use { response ->
            val source = response.body.source()
            source.request(maxBytes)
            val text = source.buffer.readUtf8(minOf(source.buffer.size, maxBytes))
            "HTTP ${response.code}\n$text"
        }
    }
}
