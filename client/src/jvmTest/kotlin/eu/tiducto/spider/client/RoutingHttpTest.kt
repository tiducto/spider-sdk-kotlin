package eu.tiducto.spider.client

import eu.tiducto.spider.client.FakeGateway.Companion.events
import eu.tiducto.spider.client.FakeGateway.Companion.json
import eu.tiducto.spider.client.FakeGateway.Reply
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drives the public routing calls end to end against [FakeGateway], so request defaults, client-side limit
 * checks, response mapping and error mapping are checked on the real engine.
 */
class RoutingHttpTest {

    private val gateway = FakeGateway()
    private val routing = SpiderRouting(gateway.baseUrl, "test-key")

    @AfterTest
    fun stop() = gateway.close()

    private val emptyDone =
        events("event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":[]}\n\n")

    private val emptyPlan = json(
        """{"data":{"planConnection":{"edges":[],"pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":[]}}}""",
    )

    private fun stream(
        maxWindow: Duration = 2.hours,
        targetResults: Int = 5,
        via: List<ViaLocation> = emptyList(),
    ) = routing.planStream(
        Location.Stop("1:A"),
        Location.Stop("1:B"),
        via = via,
        targetResults = targetResults,
        maxWindow = maxWindow,
    )

    private fun assertBadRequest(field: String, error: SpiderError) {
        val badRequest = assertIs<SpiderError.BadRequest>(error)
        assertEquals(field, badRequest.field)
        assertEquals("$field is out of range", badRequest.message)
    }

    @Test
    fun `plan sends searchWindow as given`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/plan" to emptyPlan)

        routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), searchWindow = 90.seconds)
        routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))

        val windows = gateway.seen.map {
            Json.parseToJsonElement(it.body).jsonObject
                .getValue("variables").jsonObject.getValue("searchWindow").jsonPrimitive.content
        }
        assertEquals(listOf("PT1M30S", "PT1H"), windows)
    }

    @Test
    fun `a routing HTTP 400 is a BadRequest carrying the field`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan" to Reply(400, "application/json", """{"error":"bad_request","message":"searchWindow is invalid"}"""),
        )

        val error = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error

        val badRequest = assertIs<SpiderError.BadRequest>(error)
        assertEquals("searchWindow", badRequest.field)
        assertEquals("searchWindow is invalid", badRequest.message)
        assertEquals(400, badRequest.httpStatus)
    }

    @Test
    fun `planStream always sends targetResults and maxWindow`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/plan-stream" to emptyDone)

        assertIs<PlanStreamEvent.Done>(stream(maxWindow = 2.hours, targetResults = 3).toList().single())

        val variables = gateway.variables()
        assertEquals(3, variables.getValue("targetResults").jsonPrimitive.int)
        assertEquals("PT2H", variables.getValue("maxWindow").jsonPrimitive.content)
    }

    @Test
    fun `a maxWindow under two hours fails the stream without a request`() = runBlocking<Unit> {
        val event = stream(maxWindow = 2.hours - 1.minutes).toList().single()

        assertBadRequest("maxWindow", assertIs<PlanStreamEvent.Failure>(event).error)
        assertEquals(emptyList(), gateway.seen.toList())
    }

    // The gateway answers a missing required variable itself, as a 200 JSON body before any event.
    @Test
    fun `a JSON BAD_REQUEST answer to a stream maps to BadRequest with the field`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan-stream" to json(
                """{"data":null,"errors":[{"message":"maxWindow is required","extensions":{"code":"BAD_REQUEST","field":"maxWindow"}}]}""",
            ),
        )

        val error = assertIs<PlanStreamEvent.Failure>(stream().toList().single()).error

        val badRequest = assertIs<SpiderError.BadRequest>(error)
        assertEquals("maxWindow", badRequest.field)
        assertEquals("maxWindow is required", badRequest.message)
    }

    @Test
    fun `stream routing errors arrive on Done`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan-stream" to events(
                "event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":" +
                    "[{\"code\":\"LOCATION_NOT_FOUND\",\"description\":\"no such stop\",\"inputField\":\"FROM\"}]}\n\n",
            ),
        )

        val done = assertIs<PlanStreamEvent.Done>(stream().toList().single())

        assertEquals(RoutingErrorCode.LOCATION_NOT_FOUND, done.routingErrors.single().code)
        assertEquals(InputField.FROM, done.routingErrors.single().inputField)
    }

    @Test
    fun `an unknown via stop is a LOCATION_NOT_FOUND routing error on VIA`() = runBlocking<Unit> {
        val viaNotFound = """[{"code":"LOCATION_NOT_FOUND","description":"unknown via stop","inputField":"VIA"}]"""
        gateway.replies = mapOf(
            "/routing/plan" to json(
                """{"data":{"planConnection":{"edges":[],"pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":$viaNotFound}}}""",
            ),
            "/routing/plan-stream" to events(
                "event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":$viaNotFound}\n\n",
            ),
        )
        val via = listOf(ViaLocation.PassThrough("1:NOPE"))

        val route = assertIs<SpiderResult.Success<Route>>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), via = via)).data
        val done = assertIs<PlanStreamEvent.Done>(stream(via = via).toList().single())

        for (errors in listOf(route.routingErrors, done.routingErrors)) {
            assertEquals(RoutingError(RoutingErrorCode.LOCATION_NOT_FOUND, "unknown via stop", InputField.VIA), errors.single())
        }
    }

    @Test
    fun `via limits are checked before any request on plan and planStream`() = runBlocking<Unit> {
        val invalid = listOf(
            ViaLocation.PassThrough(emptyList()),
            ViaLocation.PassThrough((1..11).map { "1:S$it" }),
            ViaLocation.Visit(Location.Stop("1:V"), minimumWaitTime = (-1).seconds),
            ViaLocation.Visit(Location.Stop("1:V"), minimumWaitTime = 24.hours + 1.seconds),
        )
        for (via in invalid) {
            val planError = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), via = listOf(via))).error
            assertBadRequest("via", planError)
            assertBadRequest("via", assertIs<PlanStreamEvent.Failure>(stream(via = listOf(via)).toList().single()).error)
        }
        assertEquals(emptyList(), gateway.seen.toList())

        gateway.replies = mapOf("/routing/plan" to emptyPlan)
        val atTheLimits = listOf(
            ViaLocation.PassThrough((1..10).map { "1:S$it" }),
            ViaLocation.Visit(Location.Stop("1:V"), minimumWaitTime = 24.hours),
        )
        assertIs<SpiderResult.Success<Route>>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), via = atTheLimits))
    }

    @Test
    fun `a retired query is QueryRetired on plan and planStream`() = runBlocking<Unit> {
        val retired = Reply(410, "application/json", """{"error":"query_retired","message":"persisted query is retired"}""")
        gateway.replies = mapOf("/routing/plan" to retired, "/routing/plan-stream" to retired)

        val planError = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error
        val streamError = assertIs<PlanStreamEvent.Failure>(stream().toList().single()).error

        for (error in listOf(planError, streamError)) {
            assertIs<SpiderError.QueryRetired>(error)
            assertEquals(SpiderErrorCode.QUERY_RETIRED, error.code)
            assertEquals("query_retired", error.code.wireName)
            assertEquals(410, error.httpStatus)
            assertEquals("query_retired", error.serverCode)
            assertEquals(true, "persisted query is retired" in error.message, error.message)
            assertFalse("update" in error.message.lowercase(), error.message)
        }
    }

    @Test
    fun `a bare 410 is QueryRetired too`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/departures" to Reply(410, "text/plain", ""))

        val error = assertIs<SpiderResult.Error>(routing.departures("1:S")).error

        assertIs<SpiderError.QueryRetired>(error)
        assertEquals(true, "persisted query is retired" in error.message, error.message)
    }

    @Test
    fun `an unknown query id stays an Unauthorized carrying the gateway code`() = runBlocking<Unit> {
        val rejected = Reply(403, "application/json", """{"error":"persisted_query_rejected","message":"unknown persisted-query id: abc"}""")
        gateway.replies = mapOf("/routing/plan" to rejected, "/routing/plan-stream" to rejected)

        val planError = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error
        val streamError = assertIs<PlanStreamEvent.Failure>(stream().toList().single()).error

        for (error in listOf(planError, streamError)) {
            assertIs<SpiderError.Unauthorized>(error)
            assertEquals(403, error.httpStatus)
            assertEquals(UNKNOWN_QUERY_SERVER_CODE, error.serverCode)
            assertEquals(true, "unknown persisted-query id" in error.message, error.message)
            assertFalse("update" in error.message.lowercase(), error.message)
        }
    }

    @Test
    fun `a plain 403 stays a key problem`() = runBlocking<Unit> {
        val plain403 = Reply(403, "application/json", """{"message":"Access to this API has been disallowed"}""")
        gateway.replies = mapOf("/routing/plan" to plain403, "/routing/plan-stream" to plain403)

        val planError = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error
        val streamError = assertIs<PlanStreamEvent.Failure>(stream().toList().single()).error

        for (error in listOf(planError, streamError)) {
            assertIs<SpiderError.Unauthorized>(error)
            assertEquals(null, error.serverCode)
        }
    }

    private fun searchLimit(status: Int = 403) =
        Reply(status, "application/json", """{"error":"search_limit_reached","message":"search limit reached"}""")

    private fun agreementInactive(status: Int = 403) =
        Reply(status, "application/json", """{"error":"agreement_inactive","message":"agreement is not active"}""")

    private suspend fun planError() = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error

    private suspend fun streamError() = assertIs<PlanStreamEvent.Failure>(stream().toList().single()).error

    @Test
    fun `a search limit refusal is SearchLimitReached on plan and planStream`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/plan" to searchLimit(), "/routing/plan-stream" to searchLimit())

        for (error in listOf(planError(), streamError())) {
            assertIs<SpiderError.SearchLimitReached>(error)
            assertEquals(SpiderErrorCode.SEARCH_LIMIT_REACHED, error.code)
            assertEquals("search_limit_reached", error.code.wireName)
            assertEquals(403, error.httpStatus)
            assertEquals("search_limit_reached", error.serverCode)
            assertEquals("search limit reached", error.message)
        }
    }

    @Test
    fun `an inactive agreement is AgreementInactive on every routing call`() = runBlocking<Unit> {
        gateway.replies = listOf("/routing/plan", "/routing/plan-stream", "/routing/departures", "/routing/trip")
            .associateWith { agreementInactive() }

        val errors = listOf(
            planError(),
            streamError(),
            assertIs<SpiderResult.Error>(routing.departures("1:S")).error,
            assertIs<SpiderResult.Error>(routing.trip("1:T", "2026-09-28")).error,
        )

        for (error in errors) {
            assertIs<SpiderError.AgreementInactive>(error)
            assertEquals(SpiderErrorCode.AGREEMENT_INACTIVE, error.code)
            assertEquals("agreement_inactive", error.code.wireName)
            assertEquals(403, error.httpStatus)
            assertEquals("agreement_inactive", error.serverCode)
            assertEquals("agreement is not active", error.message)
        }
    }

    @Test
    fun `a plan limit code decides over a status a proxy rewrote`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan" to searchLimit(400),
            "/routing/plan-stream" to searchLimit(429),
            "/routing/departures" to agreementInactive(429),
            "/routing/trip" to agreementInactive(410),
        )

        val limited = listOf(planError() to 400, streamError() to 429)
        val inactive = listOf(
            assertIs<SpiderResult.Error>(routing.departures("1:S")).error to 429,
            assertIs<SpiderResult.Error>(routing.trip("1:T", "2026-09-28")).error to 410,
        )

        for ((error, status) in limited) {
            assertIs<SpiderError.SearchLimitReached>(error)
            assertEquals(status, error.httpStatus)
            assertEquals("search_limit_reached", error.serverCode)
        }
        for ((error, status) in inactive) {
            assertIs<SpiderError.AgreementInactive>(error)
            assertEquals(status, error.httpStatus)
            assertEquals("agreement_inactive", error.serverCode)
        }
    }

    // A night departure after midnight belongs to the previous service date; rows whose headsign matches the
    // stop name are real departures (the router drops a trip's final stop itself).
    @Test
    fun `departures send the defaults and carry the service date for every row`() = runBlocking<Unit> {
        val serviceDay = 1_790_546_400L // 2026-09-28 in Europe/Prague
        gateway.replies = mapOf(
            "/routing/departures" to json(
                """
                {"data":{"asStation":{"gtfsId":"1:S","name":"Zvonařka","stoptimesWithoutPatterns":[
                  {"serviceDay":$serviceDay,"scheduledDeparture":81000,"headsign":"Zvonařka",
                   "stop":{"gtfsId":"1:S1","platformCode":"B"},
                   "trip":{"gtfsId":"1:T44","wheelchairAccessible":"POSSIBLE",
                     "route":{"gtfsId":"1:L44","shortName":"44","mode":"BUS","color":"FF0000","textColor":"FFFFFF"}}},
                  {"serviceDay":$serviceDay,"scheduledDeparture":88800,"realtimeState":"CANCELED","headsign":"Líšeň",
                   "trip":{"gtfsId":"1:N89","route":{"gtfsId":"1:LN89","shortName":"N89","mode":"BUS"}}}
                ]}}}
                """.trimIndent(),
            ),
        )

        val departures = assertIs<SpiderResult.Success<List<Departure>>>(routing.departures("1:S")).data

        assertEquals(listOf("1:T44", "1:N89"), departures.map { it.tripGtfsId })
        assertEquals(listOf("2026-09-28", "2026-09-28"), departures.map { it.serviceDate })
        assertEquals(RealtimeState.CANCELED, departures[1].realtimeState)
        val (full, bare) = departures
        assertEquals("1:L44", full.routeGtfsId)
        assertEquals("FF0000", full.routeColor)
        assertEquals("FFFFFF", full.routeTextColor)
        assertEquals("1:S1", full.stopGtfsId)
        assertEquals("B", full.platformCode)
        assertEquals(WheelchairBoarding.POSSIBLE, full.wheelchairAccessible)
        assertEquals(listOf(null, null, null, null), listOf(bare.routeColor, bare.routeTextColor, bare.stopGtfsId, bare.platformCode))
        assertEquals(null, bare.wheelchairAccessible)
        val variables = gateway.variables()
        assertEquals(30, variables.getValue("numberOfDepartures").jsonPrimitive.int)
        assertEquals(86_400, variables.getValue("timeRange").jsonPrimitive.int)
    }

    @Test
    fun `a departures timeRange outside one second to 24 hours is a BadRequest without a request`() = runBlocking<Unit> {
        for (timeRange in listOf(Duration.ZERO, (-1).hours, 24.hours + 1.seconds)) {
            assertBadRequest("timeRange", assertIs<SpiderResult.Error>(routing.departures("1:S", timeRange = timeRange)).error)
        }
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `trip reports its service date and display fields`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/trip" to json(
                """
                {"data":{"trip":{"gtfsId":"1:N89","wheelchairAccessible":"NO_INFORMATION","bikesAllowed":"SOMETHING_NEW",
                  "route":{"gtfsId":"1:LN89","shortName":"N89","color":"00AA00","textColor":"000000"},"stoptimesForDate":[
                  {"serviceDay":1790546400,"scheduledDeparture":88800,
                   "stop":{"gtfsId":"1:U1","name":"Líšeň","wheelchairBoarding":"POSSIBLE","platformCode":"2","zoneId":"101"}},
                  {"serviceDay":1790546400,"scheduledDeparture":89400,"stop":{"gtfsId":"1:U2","name":"Jírova"}}
                ]}}}
                """.trimIndent(),
            ),
        )

        val trip = assertIs<SpiderResult.Success<TripDetails>>(routing.trip("1:N89", "2026-09-28")).data

        assertEquals("2026-09-28", trip.serviceDate)
        assertEquals("1:LN89", trip.routeGtfsId)
        assertEquals("00AA00", trip.routeColor)
        assertEquals("000000", trip.routeTextColor)
        assertEquals(null, trip.wheelchairAccessible)
        assertEquals(BikesAllowed.UNKNOWN, trip.bikesAllowed)
        val (first, second) = trip.stops
        assertEquals(WheelchairBoarding.POSSIBLE, first.wheelchairBoarding)
        assertEquals("2", first.platformCode)
        assertEquals("101", first.zoneId)
        assertEquals(null, second.platformCode)
        assertEquals(null, second.zoneId)
    }

    @Test
    fun `a malformed service date is a BadRequest without a request`() = runBlocking<Unit> {
        val tripError = assertIs<SpiderResult.Error>(routing.trip("1:T", "20260928")).error
        val delaysError = assertIs<SpiderResult.Error>(
            SpiderRealtime(gateway.baseUrl, "test-key").delays(listOf("1:T"), "2026-02-30"),
        ).error

        for (error in listOf(tripError, delaysError)) {
            val badRequest = assertIs<SpiderError.BadRequest>(error)
            assertEquals("serviceDate", badRequest.field)
            assertEquals("serviceDate is invalid", badRequest.message)
        }
        assertEquals(emptyList(), gateway.seen.toList())
    }
}
