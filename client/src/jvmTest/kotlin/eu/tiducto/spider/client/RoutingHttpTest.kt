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
import kotlinx.serialization.json.JsonObject
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

    private val pageInfoEvent =
        "event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":[]}\n\n"

    private val doneEvent =
        "event: done\ndata: {\"iterations\":0,\"windowSeconds\":0,\"resultCount\":0,\"stoppedBy\":\"maxWindow\"}\n\n"

    private val emptyDone = events(pageInfoEvent + doneEvent)

    private val chunkEvent =
        "event: chunk\ndata: {\"frontier\":600,\"found\":1,\"finalized\":1,\"results\":[{\"numberOfTransfers\":0," +
            "\"duration\":600,\"legs\":[{\"mode\":\"TRAM\",\"start\":{\"scheduledTime\":\"t1\"}," +
            "\"end\":{\"scheduledTime\":\"t2\"},\"from\":{\"name\":\"A\"},\"to\":{\"name\":\"B\"}}]}]}\n\n"

    private val emptyPlan = json(
        """{"itineraries":[],"pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":[]}""",
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

    private fun bodies(): List<JsonObject> = gateway.seen.map { Json.parseToJsonElement(it.body).jsonObject }

    private fun assertBadRequest(field: String, error: SpiderError, state: String = "out of range") {
        val badRequest = assertIs<SpiderError.BadRequest>(error)
        assertEquals(field, badRequest.field)
        assertEquals("$field is $state", badRequest.message)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `plan posts the request body itself and maps itineraries to edges`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan" to json(
                """
                {"itineraries":[{"numberOfTransfers":0,"duration":600,"legs":[
                  {"mode":"TRAM","start":{"scheduledTime":"t1"},"end":{"scheduledTime":"t2"},"from":{"name":"A"},"to":{"name":"B"}}
                ]}],
                "pageInfo":{"hasNextPage":true,"hasPreviousPage":false,"startCursor":"c-prev","endCursor":"c-next","searchWindowUsed":"PT1H"},
                "routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}
                """.trimIndent(),
            ),
        )

        val route = assertIs<SpiderResult.Success<Route>>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).data

        assertEquals("/routing/plan", gateway.seen.single().path)
        val body = gateway.body()
        assertEquals(setOf("dateTime", "origin", "destination", "searchWindow"), body.keys)
        val edge = route.edges.single()
        assertEquals("NoCursor", edge.cursor)
        assertEquals(TransitMode.TRAM, edge.itinerary.legs.single().mode)
        assertEquals(null, edge.itinerary.accessibilityScore)
        assertEquals("c-next", route.pageInfo.endCursor)
        assertEquals("2026-10-07T08:00:00+02:00", route.searchDateTime)
    }

    @Test
    fun `plan sends searchWindow as given`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/plan" to emptyPlan)

        routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), searchWindow = 90.seconds)
        routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))

        assertEquals(listOf("PT1M30S", "PT1H"), bodies().map { it.getValue("searchWindow").jsonPrimitive.content })
    }

    @Test
    fun `planNext and planPrevious send the original body plus one cursor`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":true,"hasPreviousPage":true,"startCursor":"c-prev","endCursor":"c-next"},"routingErrors":[]}""",
            ),
        )

        val first = assertIs<SpiderResult.Success<Route>>(
            routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), maxTransfers = 1),
        ).data
        routing.planNext(first)
        routing.planPrevious(first)

        val (original, next, previous) = bodies()
        assertEquals(JsonObject(original + ("after" to Json.parseToJsonElement("\"c-next\""))), next)
        assertEquals(JsonObject(original + ("before" to Json.parseToJsonElement("\"c-prev\""))), previous)
    }

    @Test
    fun `plan and planStream send reliability only when set`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/plan" to emptyPlan, "/routing/plan-stream" to emptyDone)

        routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), reliability = Reliability.SAFE)
        routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))
        routing.planStream(
            Location.Stop("1:A"), Location.Stop("1:B"),
            targetResults = 5, maxWindow = 2.hours, reliability = Reliability.STANDARD,
        ).toList()
        stream().toList()

        assertEquals(listOf("SAFE", null, "STANDARD", null), bodies().map { it["reliability"]?.jsonPrimitive?.content })
    }

    @Test
    fun `a routing HTTP 400 is a BadRequest carrying the body field`() = runBlocking<Unit> {
        val field = "preferences.transit.transfer.maximumTransfers"
        val invalid = Reply(400, "application/json", """{"code":"bad_request","message":"$field is out of range","field":"$field"}""")
        gateway.replies = mapOf(
            "/routing/plan" to invalid,
            "/routing/plan-stream" to invalid,
            "/routing/departures" to Reply(400, "application/json", """{"code":"bad_request","message":"id is required","field":"id"}"""),
        )

        val errors = listOf(
            assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), maxTransfers = 99)).error,
            assertIs<PlanStreamEvent.Failure>(stream().toList().single()).error,
        )

        for (error in errors) {
            assertBadRequest(field, error)
            assertEquals(400, error.httpStatus)
        }
        assertBadRequest("id", assertIs<SpiderResult.Error>(routing.departures("")).error, state = "required")
    }

    @Test
    fun `a routing HTTP 400 without a field names the one its message names`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan" to Reply(400, "application/json", """{"code":"bad_request","message":"via.visit.coordinate is not allowed"}"""),
        )

        val error = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error

        assertBadRequest("via.visit.coordinate", error, state = "not allowed")
    }

    @Test
    fun `planStream posts the body with an event-stream Accept and always sends targetResults and maxWindow`() =
        runBlocking<Unit> {
            gateway.replies = mapOf("/routing/plan-stream" to emptyDone)

            assertIs<PlanStreamEvent.Done>(stream(maxWindow = 2.hours, targetResults = 3).toList().single())

            val seen = gateway.seen.single()
            assertEquals("/routing/plan-stream", seen.path)
            assertEquals(setOf("text/event-stream"), seen.accept.flatMap { it.split(',') }.map { it.trim() }.toSet())
            val body = gateway.body()
            assertEquals(3, body.getValue("targetResults").jsonPrimitive.int)
            assertEquals("PT2H", body.getValue("maxWindow").jsonPrimitive.content)
            assertEquals(null, body["searchWindow"])
        }

    @Test
    fun `a maxWindow under two hours fails the stream without a request`() = runBlocking<Unit> {
        val event = stream(maxWindow = 2.hours - 1.minutes).toList().single()

        assertBadRequest("maxWindow", assertIs<PlanStreamEvent.Failure>(event).error)
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `a stream maps chunks to Results then ends at Done and skips the done frame`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/plan-stream" to events(chunkEvent + chunkEvent + pageInfoEvent + doneEvent))

        val received = stream().toList()

        assertEquals(3, received.size)
        assertIs<PlanStreamEvent.Result>(received[0])
        assertIs<PlanStreamEvent.Result>(received[1])
        assertIs<PlanStreamEvent.Done>(received[2])
    }

    @Test
    fun `a stream skips event names it does not know`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan-stream" to events(
                "event: progress\ndata: {\"frontier\":60}\n\n" +
                    chunkEvent +
                    "event: error\ndata: {\"code\":\"server\",\"message\":\"boom\"}\n\n" +
                    pageInfoEvent +
                    doneEvent,
            ),
        )

        val received = stream().toList()

        assertEquals(2, received.size)
        assertIs<PlanStreamEvent.Result>(received[0])
        assertIs<PlanStreamEvent.Done>(received[1])
    }

    @Test
    fun `a stream cut before pageInfo ends in a transport Failure`() = runBlocking<Unit> {
        for (cut in listOf("", chunkEvent, "event: error\ndata: {\"message\":\"boom\"}\n\n")) {
            gateway.replies = mapOf("/routing/plan-stream" to events(cut))

            val received = stream().toList()

            val failure = assertIs<PlanStreamEvent.Failure>(received.last())
            assertIs<SpiderError.Server>(failure.error)
            assertEquals(received.dropLast(1), received.filterIsInstance<PlanStreamEvent.Result>())
        }
    }

    @Test
    fun `stream routing errors arrive on Done`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan-stream" to events(
                "event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":" +
                    "[{\"code\":\"LOCATION_NOT_FOUND\",\"description\":\"no such stop\",\"inputField\":\"FROM\"}]}\n\n" +
                    "event: done\ndata: {\"iterations\":0,\"windowSeconds\":0,\"resultCount\":0,\"stoppedBy\":\"rejected\"}\n\n",
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
                """{"itineraries":[],"pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":$viaNotFound}""",
            ),
            "/routing/plan-stream" to events(
                "event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":$viaNotFound}\n\n" +
                    doneEvent,
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
            ViaLocation.Visit(Location.Stop("1:V"), minimumWaitTime = 1.hours + 1.seconds),
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
            ViaLocation.Visit(Location.Stop("1:V"), minimumWaitTime = 1.hours),
        )
        assertIs<SpiderResult.Success<Route>>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), via = atTheLimits))
    }

    @Test
    fun `a coordinate visit is an invalid via without a request on plan and planStream`() = runBlocking<Unit> {
        val via = listOf(ViaLocation.Visit(Location.Coordinate(49.21, 16.59), minimumWaitTime = 10.minutes))

        val planError = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), via = via)).error
        val streamError = assertIs<PlanStreamEvent.Failure>(stream(via = via).toList().single()).error

        for (error in listOf(planError, streamError)) {
            assertBadRequest("via", error, state = "invalid")
            assertEquals(null, error.httpStatus)
        }
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `a retired API part is QueryRetired on every routing call`() = runBlocking<Unit> {
        for (retired in listOf(
            Reply(410, "application/json", """{"code":"query_retired","message":"persisted queries are retired"}"""),
            Reply(410, "application/json", """{"error":"query_retired","message":"persisted queries are retired"}"""),
        )) {
            gateway.replies = listOf("/routing/plan", "/routing/plan-stream", "/routing/departures", "/routing/trip")
                .associateWith { retired }

            val errors = listOf(
                planError(),
                streamError(),
                assertIs<SpiderResult.Error>(routing.departures("1:S")).error,
                assertIs<SpiderResult.Error>(routing.trip("1:T", "2026-09-28")).error,
            )

            for (error in errors) {
                assertIs<SpiderError.QueryRetired>(error)
                assertEquals(SpiderErrorCode.QUERY_RETIRED, error.code)
                assertEquals("query_retired", error.code.wireName)
                assertEquals(410, error.httpStatus)
                assertEquals("query_retired", error.serverCode)
                assertEquals(true, "persisted queries are retired" in error.message, error.message)
                assertFalse("update" in error.message.lowercase(), error.message)
            }
        }
    }

    @Test
    fun `a bare 410 is QueryRetired too`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/departures" to Reply(410, "text/plain", ""))

        val error = assertIs<SpiderResult.Error>(routing.departures("1:S")).error

        assertIs<SpiderError.QueryRetired>(error)
        assertEquals(true, "the API this call uses is retired" in error.message, error.message)
    }

    @Test
    fun `a plain 403 stays a key problem`() = runBlocking<Unit> {
        val plain403 = Reply(403, "application/json", """{"message":"Access to this API has been disallowed"}""")
        gateway.replies = mapOf("/routing/plan" to plain403, "/routing/plan-stream" to plain403)

        for (error in listOf(planError(), streamError())) {
            assertIs<SpiderError.Unauthorized>(error)
            assertEquals(null, error.serverCode)
        }
    }

    private fun planningLimit(status: Int = 403) =
        Reply(status, "application/json", """{"error":"planning_limit_reached","message":"trip planning limit reached"}""")

    private fun agreementInactive(status: Int = 403) =
        Reply(status, "application/json", """{"error":"agreement_inactive","message":"agreement is not active"}""")

    private suspend fun planError() = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error

    private suspend fun streamError() = assertIs<PlanStreamEvent.Failure>(stream().toList().single()).error

    @Test
    fun `a planning limit refusal is PlanningLimitReached on plan and planStream`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/plan" to planningLimit(), "/routing/plan-stream" to planningLimit())

        for (error in listOf(planError(), streamError())) {
            assertIs<SpiderError.PlanningLimitReached>(error)
            assertEquals(SpiderErrorCode.PLANNING_LIMIT_REACHED, error.code)
            assertEquals("planning_limit_reached", error.code.wireName)
            assertEquals(403, error.httpStatus)
            assertEquals("planning_limit_reached", error.serverCode)
            assertEquals("trip planning limit reached", error.message)
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
    fun `a plan limit refusal without a message reads the fixed wording on every routing call`() = runBlocking<Unit> {
        for (message in listOf(null, "", "   ")) {
            val extra = message?.let { ""","message":"$it"""" }.orEmpty()
            val limited = Reply(403, "application/json", """{"error":"planning_limit_reached"$extra}""")
            val inactive = Reply(403, "application/json", """{"error":"agreement_inactive"$extra}""")
            gateway.replies = mapOf(
                "/routing/plan" to limited,
                "/routing/plan-stream" to limited,
                "/routing/departures" to inactive,
                "/routing/trip" to inactive,
            )

            for (error in listOf(planError(), streamError())) {
                assertIs<SpiderError.PlanningLimitReached>(error)
                assertEquals("trip planning limit reached", error.message)
            }
            val departuresError = assertIs<SpiderResult.Error>(routing.departures("1:S")).error
            val tripError = assertIs<SpiderResult.Error>(routing.trip("1:T", "2026-09-28")).error
            for (error in listOf(departuresError, tripError)) {
                assertIs<SpiderError.AgreementInactive>(error)
                assertEquals("agreement is not active", error.message)
            }
        }
    }

    @Test
    fun `a plan limit code in the body code field decides too`() = runBlocking<Unit> {
        val codeOnly = Reply(403, "application/json", """{"code":"agreement_inactive","message":"agreement is not active"}""")
        gateway.replies = mapOf("/routing/plan" to codeOnly, "/routing/plan-stream" to codeOnly)

        for (error in listOf(planError(), streamError())) {
            assertIs<SpiderError.AgreementInactive>(error)
            assertEquals(403, error.httpStatus)
            assertEquals("agreement_inactive", error.serverCode)
        }
    }

    @Test
    fun `a plan limit code decides over a status a proxy rewrote`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/plan" to planningLimit(400),
            "/routing/plan-stream" to planningLimit(429),
            "/routing/departures" to agreementInactive(429),
            "/routing/trip" to agreementInactive(410),
        )

        val limited = listOf(planError() to 400, streamError() to 429)
        val inactive = listOf(
            assertIs<SpiderResult.Error>(routing.departures("1:S")).error to 429,
            assertIs<SpiderResult.Error>(routing.trip("1:T", "2026-09-28")).error to 410,
        )

        for ((error, status) in limited) {
            assertIs<SpiderError.PlanningLimitReached>(error)
            assertEquals(status, error.httpStatus)
            assertEquals("planning_limit_reached", error.serverCode)
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
                {"stop":{"gtfsId":"1:S","name":"Zvonařka","wheelchairBoarding":null,"stoptimesWithoutPatterns":[
                  {"serviceDay":$serviceDay,"scheduledDeparture":81000,"headsign":"Zvonařka","typicalDelay":120,
                   "stop":{"gtfsId":"1:S1","platformCode":"B"},
                   "trip":{"gtfsId":"1:T44","wheelchairAccessible":"POSSIBLE",
                     "route":{"gtfsId":"1:L44","shortName":"44","mode":"BUS","color":"FF0000","textColor":"FFFFFF"}}},
                  {"serviceDay":$serviceDay,"scheduledDeparture":88800,"realtimeState":"CANCELED","headsign":"Líšeň",
                   "trip":{"gtfsId":"1:N89","route":{"gtfsId":"1:LN89","shortName":"N89","mode":"BUS"}}}
                ]}}
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
        assertEquals(2.minutes, full.typicalDelay)
        assertEquals(null, bare.typicalDelay)
        assertEquals(
            Json.parseToJsonElement("""{"id":"1:S","numberOfDepartures":30,"timeRange":86400}"""),
            gateway.body(),
        )
    }

    @Test
    fun `an unknown stop or trip id is NotFound`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/departures" to json("""{"stop":null}"""),
            "/routing/trip" to json("""{"trip":null}"""),
        )

        assertIs<SpiderError.NotFound>(assertIs<SpiderResult.Error>(routing.departures("1:NOPE")).error)
        assertIs<SpiderError.NotFound>(assertIs<SpiderResult.Error>(routing.trip("1:NOPE")).error)
        assertEquals(listOf("""{"id":"1:NOPE"}"""), gateway.seen.filter { it.path == "/routing/trip" }.map { it.body })
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
                {"trip":{"gtfsId":"1:N89","wheelchairAccessible":"NO_INFORMATION","bikesAllowed":"SOMETHING_NEW",
                  "route":{"gtfsId":"1:LN89","shortName":"N89","color":"00AA00","textColor":"000000"},"stoptimesForDate":[
                  {"serviceDay":1790546400,"scheduledDeparture":88800,"typicalDelay":45,
                   "stop":{"gtfsId":"1:U1","name":"Líšeň","wheelchairBoarding":"POSSIBLE","platformCode":"2","zoneId":"101"}},
                  {"serviceDay":1790546400,"scheduledDeparture":89400,"stop":{"gtfsId":"1:U2","name":"Jírova"}}
                ]}}
                """.trimIndent(),
            ),
        )

        val trip = assertIs<SpiderResult.Success<TripDetails>>(routing.trip("1:N89", "2026-09-28")).data

        assertEquals(Json.parseToJsonElement("""{"id":"1:N89","serviceDate":"2026-09-28"}"""), gateway.body())
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
        assertEquals(45.seconds, first.typicalDelay)
        assertEquals(null, second.typicalDelay)
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
