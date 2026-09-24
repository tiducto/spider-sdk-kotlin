package eu.tiducto.spider.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * Verifies the client apikey actually lands on real outbound requests via [installApiKey]'s DefaultRequest
 * — the shared client setup all three surfaces (routing, stops, Realtime) install. Captured off the request
 * a MockEngine sees, so it exercises the same code path as production (the platform default engine is the
 * only substitution). JVM-only because MockEngine is the JVM test engine, but [installApiKey] and
 * [spiderHeaders] are commonMain, identical on every target.
 */
class ApiKeyHeaderTest {

    private fun clientCapturing(sink: (Headers) -> Unit): HttpClient =
        HttpClient(
            MockEngine { request: HttpRequestData ->
                sink(request.headers)
                respond(content = "{}", status = HttpStatusCode.OK)
            },
        ) {
            installApiKey("test-key")
        }

    @Test
    fun `apikey rides every request via DefaultRequest even a bare warm-up GET`() = runBlocking {
        var seen: Headers? = null
        val client = clientCapturing { seen = it }

        // A bare GET with no per-call headers (the warm-up shape) must still carry the apikey.
        client.get("https://env.example/ping")

        assertEquals("test-key", seen?.get("apikey"))
    }

    @Test
    fun `contract calls carry the apikey plus the contract and sdk identity`() = runBlocking {
        var seen: Headers? = null
        val client = clientCapturing { seen = it }

        client.post("https://env.example/routing/plan") { spiderHeaders() }

        assertEquals("test-key", seen?.get("apikey"))
        assertEquals(SpiderContract.VERSION, seen?.get(SpiderContract.HEADER))
        assertEquals(SpiderSdk.IDENTITY, seen?.get(SpiderSdk.HEADER))
    }
}
