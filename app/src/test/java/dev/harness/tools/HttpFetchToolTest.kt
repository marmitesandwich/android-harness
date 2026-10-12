package dev.harness.tools

import dev.harness.backend.HttpFetcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeHttp(private val body: String = "HTTP 200\nhi") : HttpFetcher {
    val urls = mutableListOf<String>()
    override suspend fun get(url: String): String { urls += url; return body }
}

class HttpFetchToolTest {

    @Test
    fun `fetches an https url`() = runTest {
        val http = FakeHttp()
        val output = HttpFetchTool(http).run(buildJsonObject { put("url", " https://example.com/a?b=1 ") })
        assertEquals("HTTP 200\nhi", output)
        assertEquals(listOf("https://example.com/a?b=1"), http.urls)
    }

    @Test
    fun `refuses other schemes without fetching`() = runTest {
        val http = FakeHttp()
        listOf("file:///sdcard/x", "content://sms/inbox", "ftp://x", "example.com").forEach { url ->
            val error = runCatching { HttpFetchTool(http).run(buildJsonObject { put("url", url) }) }
            assertTrue(url, error.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(http.urls.isEmpty())
    }

    @Test
    fun `needs approval because a url can carry data out`() {
        assertEquals(Risk.WRITE, HttpFetchTool(FakeHttp()).risk)
    }
}
