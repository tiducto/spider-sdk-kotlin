package eu.tiducto.spider.client

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

class RetryConfig {
    var maxAttempts: Int = 3
}

abstract class SurfaceConfig {
    internal var retry: RetryConfig? = null

    fun autoRetry(block: RetryConfig.() -> Unit = {}) {
        retry = RetryConfig().apply(block)
    }
}

internal fun HttpClientConfig<*>.installAutoRetry(
    retry: RetryConfig?,
    sleep: (suspend (Long) -> Unit)? = null,
) {
    retry ?: return
    val retries = (retry.maxAttempts - 1).coerceAtLeast(0)
    install(HttpRequestRetry) {
        retryOnExceptionIf(maxRetries = retries) { _, cause -> cause !is CancellationException }
        retryIf(maxRetries = retries) { _, response -> response.status.value == 429 || response.status.value >= 500 }
        delayMillis(respectRetryAfterHeader = false) { attempt ->
            retryDelayMillis(attempt, response?.headers?.get(HttpHeaders.RetryAfter), Random.nextDouble())
        }
        sleep?.let { delay(it) }
    }
}

// Same policy as the TypeScript, Swift and Dart SDKs: a Retry-After in (possibly fractional) seconds replaces the
// exponential backoff (1s, 2s, 4s… capped at 10s); an absent, negative or non-numeric value (incl. the HTTP-date
// form) falls back to it. [jitter] in [0, 1) adds up to 25 % on top of either.
internal fun retryDelayMillis(attempt: Int, retryAfter: String?, jitter: Double): Long {
    val retryAfterMs = retryAfter?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.times(MILLIS_PER_SECOND)
    val base = retryAfterMs ?: min(BASE_DELAY_MS * 2.0.pow(attempt - 1), MAX_BACKOFF_MS)
    return (base + base * MAX_JITTER * jitter).roundToLong()
}

private const val MILLIS_PER_SECOND = 1000.0
private const val BASE_DELAY_MS = 1000.0
private const val MAX_BACKOFF_MS = 10_000.0
private const val MAX_JITTER = 0.25
