package eu.tiducto.spider.client

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import kotlin.coroutines.cancellation.CancellationException

class RetryConfig {
    var maxAttempts: Int = 3
}

abstract class FeatureConfig {
    internal var retry: RetryConfig? = null

    fun autoRetry(block: RetryConfig.() -> Unit = {}) {
        retry = RetryConfig().apply(block)
    }
}

internal fun HttpClientConfig<*>.installAutoRetry(retry: RetryConfig?) {
    retry ?: return
    val retries = (retry.maxAttempts - 1).coerceAtLeast(0)
    install(HttpRequestRetry) {
        retryOnExceptionIf(maxRetries = retries) { _, cause -> cause !is CancellationException }
        retryIf(maxRetries = retries) { _, response -> response.status.value == 429 || response.status.value >= 500 }
        exponentialDelay()
    }
}
