package eu.tiducto.spider.client

import kotlin.test.Test
import kotlin.test.assertEquals

class RetryDelayTest {

    @Test
    fun `Retry-After seconds replace the backoff on every attempt`() {
        assertEquals(3_000, retryDelayMillis(attempt = 1, retryAfter = "3", jitter = 0.0))
        assertEquals(3_000, retryDelayMillis(attempt = 4, retryAfter = "3", jitter = 0.0))
    }

    @Test
    fun `fractional and zero Retry-After are honoured`() {
        assertEquals(1_500, retryDelayMillis(attempt = 1, retryAfter = "1.5", jitter = 0.0))
        assertEquals(0, retryDelayMillis(attempt = 3, retryAfter = "0", jitter = 0.0))
    }

    @Test
    fun `Retry-After is not capped by the backoff ceiling`() {
        assertEquals(30_000, retryDelayMillis(attempt = 1, retryAfter = "30", jitter = 0.0))
    }

    @Test
    fun `without Retry-After the backoff doubles from one second and caps at ten`() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 10_000L, 10_000L),
            (1..6).map { retryDelayMillis(attempt = it, retryAfter = null, jitter = 0.0) },
        )
    }

    @Test
    fun `an unusable Retry-After falls back to the backoff`() {
        listOf("", "soon", "-1", "NaN", "Infinity", "Wed, 21 Oct 2015 07:28:00 GMT").forEach { header ->
            assertEquals(2_000, retryDelayMillis(attempt = 2, retryAfter = header, jitter = 0.0), "header '$header'")
        }
    }

    @Test
    fun `jitter adds up to a quarter on top of either delay`() {
        assertEquals(1_250, retryDelayMillis(attempt = 1, retryAfter = null, jitter = 1.0))
        assertEquals(2_250, retryDelayMillis(attempt = 1, retryAfter = "2", jitter = 0.5))
    }
}
