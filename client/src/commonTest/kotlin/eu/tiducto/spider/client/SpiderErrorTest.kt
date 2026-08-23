package eu.tiducto.spider.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

class SpiderErrorTest {
    private fun http(status: Int) =
        SpiderTransportException.Http(status, "POST /x -> $status: body").toSpiderError()

    @Test
    fun httpStatusMapsToCode() {
        assertEquals(SpiderErrorCode.UNAUTHORIZED, http(401).code)
        assertEquals(SpiderErrorCode.UNAUTHORIZED, http(403).code)
        assertEquals(SpiderErrorCode.NOT_FOUND, http(404).code)
        assertEquals(SpiderErrorCode.TIMEOUT, http(408).code)
        assertEquals(SpiderErrorCode.TIMEOUT, http(504).code)
        assertEquals(SpiderErrorCode.RATE_LIMITED, http(429).code)
        assertEquals(SpiderErrorCode.SERVER, http(503).code)
        assertEquals(SpiderErrorCode.UNKNOWN, http(418).code)
    }

    @Test
    fun httpStatusIsExposedUniformly() {
        assertEquals(401, http(401).httpStatus)
        assertEquals(403, http(403).httpStatus)
        assertEquals(404, http(404).httpStatus)
        assertEquals(408, http(408).httpStatus)
        assertEquals(429, http(429).httpStatus)
        assertEquals(503, http(503).httpStatus)
        assertEquals(418, http(418).httpStatus)
    }

    @Test
    fun messageComesFromTheCause() {
        assertEquals("POST /x -> 401: body", http(401).message)
    }

    @Test
    fun nonHttpTransportMapsToCode() {
        assertEquals(SpiderErrorCode.NOT_FOUND, SpiderTransportException.NoData("x").toSpiderError().code)
        assertNull(SpiderTransportException.NoData("x").toSpiderError().httpStatus)
        assertEquals(SpiderErrorCode.SERVER, SpiderTransportException.Upstream("x").toSpiderError().code)
        assertEquals(SpiderErrorCode.DECODING, SerializationException("x").toSpiderError().code)
        assertEquals(SpiderErrorCode.NETWORK, IOException("x").toSpiderError().code)
    }

    @Test
    fun serverCodeComesFromTheHttpCause() {
        val error = SpiderTransportException.Http(429, "POST /x -> 429: Rate limit exceeded.", "rate_limited").toSpiderError()
        assertEquals(SpiderErrorCode.RATE_LIMITED, error.code)
        assertEquals("rate_limited", error.serverCode)
    }

    @Test
    fun serverCodeIsNullForNonHttpErrors() {
        assertNull(IOException("x").toSpiderError().serverCode)
        assertNull(http(500).serverCode)
    }

    @Test
    fun topLevelBadRequestErrorBecomesBadRequestWithFieldAndMessage() {
        // A GraphQL top-level BAD_REQUEST (over-cap searchWindow / bad via / missing required) as plan()
        // sees it: errors[] → the transport exception → the typed SpiderError.
        val errors = listOf(
            GraphQlErrorPayload(
                message = "searchWindow exceeds the maximum of PT2H",
                extensions = GraphQlErrorExtensions(code = "BAD_REQUEST", field = "searchWindow"),
            ),
        )
        val error = errors.toTransportException("plan").toSpiderError()
        assertEquals(SpiderErrorCode.BAD_REQUEST, error.code)
        assertEquals("bad_request", error.code.wireName)
        error as SpiderError.BadRequest
        assertEquals("searchWindow", error.field)
        assertEquals("searchWindow exceeds the maximum of PT2H", error.message)
    }

    @Test
    fun badRequestWithoutAFieldStillMapsWithItsMessage() {
        val errors = listOf(GraphQlErrorPayload("bad via", GraphQlErrorExtensions(code = "BAD_REQUEST")))
        val error = errors.toTransportException("plan").toSpiderError()
        assertEquals(SpiderErrorCode.BAD_REQUEST, error.code)
        assertNull((error as SpiderError.BadRequest).field)
        assertEquals("bad via", error.message)
    }

    @Test
    fun otherTopLevelErrorsStayServer() {
        val errors = listOf(GraphQlErrorPayload("internal boom", extensions = null))
        assertEquals(SpiderErrorCode.SERVER, errors.toTransportException("plan").toSpiderError().code)
    }

    @Test
    fun parseErrorEnvelopeReadsCodeAndMessageAndToleratesNonJson() {
        val envelope = parseErrorEnvelope("""{"code":"forbidden","message":"nope"}""")
        assertEquals("forbidden", envelope.code)
        assertEquals("nope", envelope.message)
        val empty = parseErrorEnvelope("plain text")
        assertNull(empty.code)
        assertNull(empty.message)
    }
}
