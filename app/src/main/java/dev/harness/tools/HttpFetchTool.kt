package dev.harness.tools

import dev.harness.backend.HttpFetcher
import kotlinx.serialization.json.JsonObject

/**
 * GET a web page. WRITE-gated on purpose: tool output is untrusted, so an injected instruction could
 * otherwise exfiltrate data through the query string with no prompt. The approval card shows the URL.
 */
class HttpFetchTool(private val http: HttpFetcher) : Tool {
    override val name = "http_fetch"
    override val description = "HTTP GET an http(s) URL and return the status and the start of the response body."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "url" to Params.str("http:// or https:// URL"),
        required = listOf("url"),
    )

    override suspend fun run(args: JsonObject): String {
        val url = Args.str(args, "url").trim()
        require(url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            "url must start with http:// or https://"
        }
        return http.get(url)
    }
}
