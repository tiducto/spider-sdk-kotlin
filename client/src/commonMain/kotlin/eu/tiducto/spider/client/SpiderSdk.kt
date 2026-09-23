package eu.tiducto.spider.client

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

/**
 * This SDK's own identity, sent on every request as [HEADER] so the platform can record which SDK and
 * version each client speaks — for deprecation notices and internal adoption metrics. Orthogonal to
 * [SpiderContract]: [SpiderContract.VERSION] is the wire protocol the request speaks, [IDENTITY] is the
 * library that spoke it.
 */
internal object SpiderSdk {

    /** Sent on every request; the value is [IDENTITY]. */
    const val HEADER: String = "x-spider-sdk"

    /**
     * `kotlin/<version>` — the language tag plus [SDK_VERSION], the published artifact version
     * (`<contract>.<patch>`). [SDK_VERSION] is generated from the Gradle project version, so this can
     * never drift from what was actually released.
     */
    const val IDENTITY: String = "kotlin/$SDK_VERSION"
}

/**
 * Installs the client apikey as a default on every request this client sends, via Ktor's DefaultRequest.
 * The apikey is invariant for the client's whole life, so it belongs in the client's shared setup —
 * installed once here rather than re-stamped per call. This covers every surface a client opens, including
 * bare requests that carry no per-call headers (e.g. the warm-up GET). One home for the apikey, alongside
 * [installAutoRetry] and [installSpiderLogging].
 */
internal fun HttpClientConfig<*>.installApiKey(apiKey: String) {
    defaultRequest {
        header("apikey", apiKey)
    }
}

/**
 * Stamps the per-request Spider headers onto an outbound request — the wire-contract version
 * ([SpiderContract.HEADER]) and this SDK's identity ([SpiderSdk.HEADER]). The apikey is not set here: it
 * rides on every request via [installApiKey]'s DefaultRequest (the client's shared setup), so it need not
 * be re-attached per call. One home for the per-request headers every surface (routing, stops, Realtime)
 * layers on top, so they stay identical.
 */
internal fun HttpRequestBuilder.spiderHeaders() {
    header(SpiderContract.HEADER, SpiderContract.VERSION)
    header(SpiderSdk.HEADER, SpiderSdk.IDENTITY)
}
