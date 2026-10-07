package eu.tiducto.spider.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
    fun routing400IsBadRequestWithTheBodyFieldAndMessage() {
        val error = routingHttpFailure(
            "plan",
            400,
            """{"code":"bad_request","message":"preferences.street.bicycle is not allowed","field":"preferences.street.bicycle"}""",
        ).toSpiderError()
        assertEquals(SpiderErrorCode.BAD_REQUEST, error.code)
        assertEquals("bad_request", error.code.wireName)
        error as SpiderError.BadRequest
        assertEquals("preferences.street.bicycle", error.field)
        assertEquals("preferences.street.bicycle is not allowed", error.message)
        assertEquals(400, error.httpStatus)
        assertEquals("bad_request", error.serverCode)
    }

    @Test
    fun theBodyFieldWinsOverTheFieldTheMessageNames() {
        val error = SpiderTransportException.Http(400, "x", detail = "via is invalid", field = "via.visit").toSpiderError()
        assertEquals("via.visit", (error as SpiderError.BadRequest).field)
    }

    @Test
    fun aBadRequestWithoutAFieldStillMapsWithItsMessage() {
        val error = routingHttpFailure("plan", 400, """{"code":"bad_request","message":"Request is too large"}""").toSpiderError()
        assertEquals(SpiderErrorCode.BAD_REQUEST, error.code)
        assertNull((error as SpiderError.BadRequest).field)
        assertEquals("Request is too large", error.message)
    }

    @Test
    fun http400IsBadRequestWithTheFieldTheMessageNames() {
        for ((detail, field) in listOf(
            "limit is out of range" to "limit",
            "maxWindow is required" to "maxWindow",
            "serviceDate is invalid" to "serviceDate",
            "preferences.transit.transfer.maximumTransfers is out of range" to "preferences.transit.transfer.maximumTransfers",
            "via.visit.coordinate is not allowed" to "via.visit.coordinate",
            "Attribute `name` is not filterable." to null,
        )) {
            val error = SpiderTransportException.Http(400, "POST /x → 400: $detail", detail = detail).toSpiderError()
            error as SpiderError.BadRequest
            assertEquals(field, error.field)
            assertEquals(detail, error.message)
            assertEquals(400, error.httpStatus)
        }
    }

    private val planningLimit = """{"error":"planning_limit_reached","message":"trip planning limit reached"}"""
    private val agreementInactive = """{"error":"agreement_inactive","message":"agreement is not active"}"""

    @Test
    fun planLimitCodesOnA403MapToTheirOwnErrors() {
        val limited = routingHttpFailure("plan", 403, planningLimit).toSpiderError()
        assertIs<SpiderError.PlanningLimitReached>(limited)
        assertEquals(SpiderErrorCode.PLANNING_LIMIT_REACHED, limited.code)
        assertEquals("planning_limit_reached", limited.code.wireName)
        assertEquals("planning_limit_reached", limited.serverCode)
        assertEquals("trip planning limit reached", limited.message)
        assertEquals(403, limited.httpStatus)

        val inactive = routingHttpFailure("departures", 403, agreementInactive).toSpiderError()
        assertIs<SpiderError.AgreementInactive>(inactive)
        assertEquals(SpiderErrorCode.AGREEMENT_INACTIVE, inactive.code)
        assertEquals("agreement_inactive", inactive.code.wireName)
        assertEquals("agreement_inactive", inactive.serverCode)
        assertEquals("agreement is not active", inactive.message)
        assertEquals(403, inactive.httpStatus)
    }

    @Test
    fun planLimitCodeDecidesWhateverTheStatus() {
        for (status in listOf(400, 404, 410, 429, 502)) {
            val limited = routingHttpFailure("plan", status, planningLimit).toSpiderError()
            assertIs<SpiderError.PlanningLimitReached>(limited)
            assertEquals(status, limited.httpStatus)
            val inactive = routingHttpFailure("trip", status, agreementInactive).toSpiderError()
            assertIs<SpiderError.AgreementInactive>(inactive)
            assertEquals(status, inactive.httpStatus)
        }
    }

    @Test
    fun planLimitMessageIsTheBodysTrimmedMessage() {
        val limited = routingHttpFailure("plan", 403, """{"error":"planning_limit_reached","message":"  over the plan  "}""")
            .toSpiderError()
        assertEquals("over the plan", limited.message)
        assertEquals("routing plan → 403: over the plan", limited.cause?.message)
    }

    @Test
    fun planLimitMessageFallsBackToTheFixedWordingWhenTheBodyHasNone() {
        for (extra in listOf("", ""","message":""""", ""","message":"   """", ""","message":null""")) {
            val limited = routingHttpFailure("plan", 403, """{"error":"planning_limit_reached"$extra}""").toSpiderError()
            assertIs<SpiderError.PlanningLimitReached>(limited)
            assertEquals("trip planning limit reached", limited.message)
            val inactive = routingHttpFailure("trip", 403, """{"error":"agreement_inactive"$extra}""").toSpiderError()
            assertIs<SpiderError.AgreementInactive>(inactive)
            assertEquals("agreement is not active", inactive.message)
        }
    }

    @Test
    fun thePlanLimitCodeReadsTheBodyCodeBeforeItsError() {
        val inactive = routingHttpFailure("plan", 403, """{"code":"agreement_inactive","message":"agreement is not active"}""")
            .toSpiderError()
        assertIs<SpiderError.AgreementInactive>(inactive)
        assertEquals("agreement_inactive", inactive.serverCode)
        val both = routingHttpFailure("plan", 403, """{"code":"planning_limit_reached","error":"planning_limit_reached"}""")
            .toSpiderError()
        assertIs<SpiderError.PlanningLimitReached>(both)
        assertEquals("trip planning limit reached", both.message)
        val codeWins = routingHttpFailure("plan", 403, """{"code":"forbidden","error":"planning_limit_reached"}""")
            .toSpiderError()
        assertIs<SpiderError.Unauthorized>(codeWins)
    }

    @Test
    fun a403WithoutAPlanLimitCodeStaysUnauthorized() {
        for (body in listOf(
            "",
            "Forbidden",
            """{"message":"Access to this API has been disallowed"}""",
            """{"error":"forbidden"}""",
        )) {
            val error = routingHttpFailure("plan", 403, body).toSpiderError()
            assertIs<SpiderError.Unauthorized>(error)
            assertEquals(403, error.httpStatus)
        }
        assertIs<SpiderError.Unauthorized>(http(403))
    }

    @Test
    fun planLimitErrorsBuiltWithoutACauseKeepTheirMessage() {
        assertEquals("trip planning limit reached", SpiderError.PlanningLimitReached().message)
        assertEquals("agreement is not active", SpiderError.AgreementInactive().message)
    }

    @Test
    fun parseErrorEnvelopeReadsCodeMessageAndFieldAndToleratesNonJson() {
        val envelope = parseErrorEnvelope("""{"code":"bad_request","message":"id is required","field":"id"}""")
        assertEquals("bad_request", envelope.code)
        assertEquals("id is required", envelope.message)
        assertEquals("id", envelope.field)
        val empty = parseErrorEnvelope("plain text")
        assertNull(empty.code)
        assertNull(empty.message)
    }
}
