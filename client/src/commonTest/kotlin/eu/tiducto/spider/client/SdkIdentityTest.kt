package eu.tiducto.spider.client

import io.ktor.client.request.HttpRequestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the request headers every outbound call stamps via [spiderHeaders]. Test names avoid `()` and
 * other Kotlin/Native-forbidden chars in backtick identifiers — commonTest runs on every target.
 */
class SdkIdentityTest {

    @Test
    fun `spiderHeaders stamps apikey, contract version, and sdk identity`() {
        val builder = HttpRequestBuilder().apply { spiderHeaders("test-key") }

        assertEquals("test-key", builder.headers["apikey"])
        assertEquals(SpiderContract.VERSION, builder.headers[SpiderContract.HEADER])
        assertEquals(SpiderSdk.IDENTITY, builder.headers[SpiderSdk.HEADER])
    }

    @Test
    fun `sdk identity is kotlin slash the published version`() {
        assertEquals("x-spider-sdk", SpiderSdk.HEADER)
        assertTrue(SDK_VERSION.isNotBlank(), "SDK_VERSION should be generated from the Gradle project version")
        assertEquals("kotlin/$SDK_VERSION", SpiderSdk.IDENTITY)
        assertTrue(SpiderSdk.IDENTITY.startsWith("kotlin/"), "identity must carry the kotlin language tag")
    }
}
