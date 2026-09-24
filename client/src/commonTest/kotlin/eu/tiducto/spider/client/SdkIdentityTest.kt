package eu.tiducto.spider.client

import io.ktor.client.request.HttpRequestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the per-request headers every outbound call stamps via [spiderHeaders] — the contract version and
 * the SDK identity. The apikey is no longer stamped here: it rides on every request via [installApiKey]'s
 * DefaultRequest (verified against a real request in the jvmTest ApiKeyHeaderTest). Test names avoid `()`
 * and other Kotlin/Native-forbidden chars in backtick identifiers — commonTest runs on every target.
 */
class SdkIdentityTest {

    @Test
    fun `spiderHeaders stamps contract version and sdk identity but not apikey`() {
        val builder = HttpRequestBuilder().apply { spiderHeaders() }

        assertEquals(SpiderContract.VERSION, builder.headers[SpiderContract.HEADER])
        assertEquals(SpiderSdk.IDENTITY, builder.headers[SpiderSdk.HEADER])
        // The apikey is a client-lifetime default (installApiKey / DefaultRequest), not a per-call header.
        assertNull(builder.headers["apikey"])
    }

    @Test
    fun `sdk identity is kotlin slash the published version`() {
        assertEquals("x-spider-sdk", SpiderSdk.HEADER)
        assertTrue(SDK_VERSION.isNotBlank(), "SDK_VERSION should be generated from the Gradle project version")
        assertEquals("kotlin/$SDK_VERSION", SpiderSdk.IDENTITY)
        assertTrue(SpiderSdk.IDENTITY.startsWith("kotlin/"), "identity must carry the kotlin language tag")
    }
}
