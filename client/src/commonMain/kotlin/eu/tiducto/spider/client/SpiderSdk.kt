package eu.tiducto.spider.client

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
 * Stamps the standard Spider request headers onto an outbound request — the raw [apiKey], the
 * wire-contract version ([SpiderContract.HEADER]), and this SDK's identity ([SpiderSdk.HEADER]). One
 * home for the headers every surface (routing, stops, Realtime) must send, so they stay identical.
 */
internal fun HttpRequestBuilder.spiderHeaders(apiKey: String) {
    header("apikey", apiKey)
    header(SpiderContract.HEADER, SpiderContract.VERSION)
    header(SpiderSdk.HEADER, SpiderSdk.IDENTITY)
}
