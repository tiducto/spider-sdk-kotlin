package eu.tiducto.spider.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

// Drives the installed HttpRequestRetry through MockEngine, recording the delays it asks for instead of sleeping.
class AutoRetryTest {

    private class Scripted(val status: HttpStatusCode, val retryAfter: String? = null)

    private fun run(maxAttempts: Int, vararg script: Scripted): Pair<HttpStatusCode, List<Long>> = runBlocking {
        val sleeps = mutableListOf<Long>()
        var call = 0
        val client = HttpClient(
            MockEngine {
                val step = script[minOf(call++, script.lastIndex)]
                val headers = step.retryAfter?.let { headersOf(HttpHeaders.RetryAfter, it) } ?: headersOf()
                respond(content = "{}", status = step.status, headers = headers)
            },
        ) {
            installAutoRetry(RetryConfig().apply { this.maxAttempts = maxAttempts }, sleep = { sleeps += it })
        }
        client.get("https://env.example/realtime/alerts").status to sleeps
    }

    private fun assertWithin(expectedBase: Long, actual: Long) =
        assertTrue(actual in expectedBase..expectedBase * 5 / 4, "delay $actual outside $expectedBase + 25 %")

    @Test
    fun `429 waits the Retry-After seconds then succeeds`() {
        val (status, sleeps) = run(3, Scripted(HttpStatusCode.TooManyRequests, "4"), Scripted(HttpStatusCode.OK))
        assertEquals(HttpStatusCode.OK, status)
        assertEquals(1, sleeps.size)
        assertWithin(4_000, sleeps.single())
    }

    @Test
    fun `503 waits the Retry-After seconds then succeeds`() {
        val (status, sleeps) = run(3, Scripted(HttpStatusCode.ServiceUnavailable, "2"), Scripted(HttpStatusCode.OK))
        assertEquals(HttpStatusCode.OK, status)
        assertWithin(2_000, sleeps.single())
    }

    @Test
    fun `a missing Retry-After backs off exponentially from one second`() {
        val (status, sleeps) = run(3, Scripted(HttpStatusCode.InternalServerError))
        assertEquals(HttpStatusCode.InternalServerError, status)
        assertEquals(2, sleeps.size)
        assertWithin(1_000, sleeps[0])
        assertWithin(2_000, sleeps[1])
    }

    @Test
    fun `an HTTP-date or garbage Retry-After falls back to the backoff`() {
        val (_, dated) = run(2, Scripted(HttpStatusCode.ServiceUnavailable, "Wed, 21 Oct 2015 07:28:00 GMT"))
        assertWithin(1_000, dated.single())
        val (_, garbage) = run(2, Scripted(HttpStatusCode.TooManyRequests, "later"))
        assertWithin(1_000, garbage.single())
    }

    @Test
    fun `a success is never retried`() {
        val (status, sleeps) = run(3, Scripted(HttpStatusCode.OK, "5"))
        assertEquals(HttpStatusCode.OK, status)
        assertTrue(sleeps.isEmpty())
    }
}
