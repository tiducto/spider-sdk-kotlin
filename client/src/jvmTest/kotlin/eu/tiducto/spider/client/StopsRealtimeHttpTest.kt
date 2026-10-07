package eu.tiducto.spider.client

import eu.tiducto.spider.client.FakeGateway.Companion.json
import eu.tiducto.spider.client.FakeGateway.Reply
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Stop search and realtime end to end against [FakeGateway]: request defaults, limit checks, mapping. */
class StopsRealtimeHttpTest {

    private val gateway = FakeGateway()
    private val stops = SpiderStops(gateway.baseUrl, "test-key")
    private val realtime = SpiderRealtime(gateway.baseUrl, "test-key")

    @AfterTest
    fun stop() = gateway.close()

    private fun assertOutOfRange(field: String, result: SpiderResult<*>) {
        val error = assertIs<SpiderError.BadRequest>(assertIs<SpiderResult.Error>(result).error)
        assertEquals(field, error.field)
        assertEquals("$field is out of range", error.message)
    }

    private fun assertBadRequest(message: String, result: SpiderResult<*>) {
        val error = assertIs<SpiderError.BadRequest>(assertIs<SpiderResult.Error>(result).error)
        assertEquals("tripIds", error.field)
        assertEquals(message, error.message)
    }

    @Test
    fun `stop search sends limit 20 by default and filters by the known modes`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/stops/v1/search" to json(
                """
                {"hits":[
                  {"gtfsId":"1:U1","name":"Náměstí Svobody","code":"NS","locationType":1,"wheelchairBoarding":1,
                   "modes":["TRAM","FUNICULAR","HOVERCRAFT"],"lat":49.19,"lon":16.61},
                  {"gtfsId":"1:U2","name":"Náměstí Míru","wheelchairBoarding":0,"lat":49.2,"lon":16.59},
                  {"gtfsId":"1:U3","name":"Náměstí Republiky","wheelchairBoarding":7,"lat":49.21,"lon":16.6}
                ],"query":"Náměstí"}
                """.trimIndent(),
            ),
        )

        val (full, bare, odd) = assertIs<SpiderResult.Success<List<Stop>>>(
            stops.search {
                filter { name eq "Náměstí" }
                modes = setOf(TransitMode.TRAM, TransitMode.UNKNOWN, TransitMode.BUS)
            },
        ).data

        val body = gateway.body()
        assertEquals(20, body.getValue("limit").jsonPrimitive.int)
        assertEquals("""modes IN ["TRAM", "BUS"]""", body.getValue("filter").jsonPrimitive.content)
        assertEquals(listOf(TransitMode.TRAM, TransitMode.FUNICULAR, TransitMode.UNKNOWN), full.modes)
        assertEquals(WheelchairBoarding.POSSIBLE, full.wheelchairBoarding)
        assertEquals("NS", full.code)
        assertEquals(1, full.locationType)
        assertEquals(emptyList(), bare.modes)
        assertEquals(null, bare.wheelchairBoarding)
        assertEquals(null, bare.code)
        assertEquals(null, bare.locationType)
        assertEquals(WheelchairBoarding.UNKNOWN, odd.wheelchairBoarding)
    }

    @Test
    fun `a modes filter of only UNKNOWN adds no filter`() = runBlocking<Unit> {
        gateway.replies = mapOf("/stops/v1/search" to json("""{"hits":[],"query":""}"""))

        stops.search { modes = setOf(TransitMode.UNKNOWN) }

        assertEquals(null, gateway.body()["filter"])
    }

    // Each surface words its 400 as "<field> is out of range|required|invalid": stops as a JSON message,
    // realtime as plain text.
    @Test
    fun `an HTTP 400 is a BadRequest carrying the field its message names`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/stops/v1/search" to Reply(400, "application/json", """{"error":"bad_request","message":"limit is required"}"""),
            "/realtime/v1/vehicles" to Reply(400, "text/plain", "tripIds is out of range\n"),
        )

        val stopsError = assertIs<SpiderError.BadRequest>(assertIs<SpiderResult.Error>(stops.near(49.19, 16.61)).error)
        val realtimeError = assertIs<SpiderError.BadRequest>(assertIs<SpiderResult.Error>(realtime.vehicles(listOf("1:T"))).error)

        assertEquals("limit", stopsError.field)
        assertEquals("limit is required", stopsError.message)
        assertEquals(400, stopsError.httpStatus)
        assertEquals("tripIds", realtimeError.field)
        assertEquals("tripIds is out of range", realtimeError.message)
        assertEquals(400, realtimeError.httpStatus)
    }

    @Test
    fun `an HTTP 400 field in the body names the field on stop search and realtime`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/stops/v1/search" to Reply(400, "application/json", """{"code":"bad_request","message":"q is invalid","field":"q"}"""),
            "/realtime/v1/delays" to Reply(
                400,
                "application/json",
                """{"code":"bad_request","message":"serviceDate is out of range","field":"serviceDate"}""",
            ),
        )

        val stopsError = assertIs<SpiderError.BadRequest>(assertIs<SpiderResult.Error>(stops.near(49.19, 16.61)).error)
        val realtimeError = assertIs<SpiderError.BadRequest>(
            assertIs<SpiderResult.Error>(realtime.delays("2026-10-07", listOf("1:T"))).error,
        )

        assertEquals("q", stopsError.field)
        assertEquals("serviceDate", realtimeError.field)
        assertEquals("serviceDate is out of range", realtimeError.message)
    }

    @Test
    fun `an HTTP 400 in another wording is a BadRequest without a field`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/stops/v1/search" to Reply(
                400,
                "application/json",
                """{"message":"Attribute `name` is not filterable.","code":"invalid_search_filter","type":"invalid_request"}""",
            ),
        )

        val error = assertIs<SpiderError.BadRequest>(assertIs<SpiderResult.Error>(stops.near(49.19, 16.61)).error)

        assertEquals(null, error.field)
        assertEquals("Attribute `name` is not filterable.", error.message)
    }

    @Test
    fun `plan limit codes map on stop search and realtime whatever the status`() = runBlocking<Unit> {
        val planningLimit = """{"error":"planning_limit_reached","message":"trip planning limit reached"}"""
        val agreementInactive = """{"error":"agreement_inactive","message":"agreement is not active"}"""
        val cases = listOf(
            Triple(Reply(403, "application/json", agreementInactive), SpiderErrorCode.AGREEMENT_INACTIVE, "agreement is not active"),
            Triple(Reply(400, "application/json", agreementInactive), SpiderErrorCode.AGREEMENT_INACTIVE, "agreement is not active"),
            Triple(Reply(403, "application/json", planningLimit), SpiderErrorCode.PLANNING_LIMIT_REACHED, "trip planning limit reached"),
        )
        for ((reply, code, message) in cases) {
            gateway.replies = mapOf("/stops/v1/search" to reply, "/realtime/v1/vehicles" to reply, "/realtime/v1/alerts" to reply)

            val errors = listOf(
                assertIs<SpiderResult.Error>(stops.search { filter { name eq "Náměstí" } }).error,
                assertIs<SpiderResult.Error>(realtime.vehicles(listOf("1:T"))).error,
                assertIs<SpiderResult.Error>(realtime.alerts()).error,
            )

            for (error in errors) {
                assertEquals(code, error.code)
                assertEquals(code.wireName, error.serverCode)
                assertEquals(message, error.message)
                assertEquals(reply.status, error.httpStatus)
            }
        }
    }

    @Test
    fun `a plan limit code on a 404 for a trip vehicle is that error`() = runBlocking<Unit> {
        val cases = listOf(
            """{"error":"planning_limit_reached","message":"trip planning limit reached"}""" to SpiderErrorCode.PLANNING_LIMIT_REACHED,
            """{"error":"agreement_inactive","message":"agreement is not active"}""" to SpiderErrorCode.AGREEMENT_INACTIVE,
        )
        for ((body, code) in cases) {
            gateway.replies = mapOf("/realtime/v1/vehicles/by-trip/T1" to Reply(404, "application/json", body))

            val error = assertIs<SpiderResult.Error>(realtime.vehicleForTrip("T1")).error

            assertEquals(code, error.code)
            assertEquals(code.wireName, error.serverCode)
            assertEquals(404, error.httpStatus)
        }
    }

    @Test
    fun `a plain 404 for a trip vehicle is still no vehicle`() = runBlocking<Unit> {
        for (reply in listOf(
            Reply(404, "application/json", "{}"),
            Reply(404, "application/json", """{"error":"not_found","message":"no vehicle for trip"}"""),
            Reply(404, "text/plain", ""),
        )) {
            gateway.replies = mapOf("/realtime/v1/vehicles/by-trip/T1" to reply)

            val update = assertIs<SpiderResult.Success<LiveVehicleUpdate>>(realtime.vehicleForTrip("T1")).data

            assertEquals(null, update.vehicle)
        }
    }

    @Test
    fun `a plan limit refusal without a message reads the fixed wording on stop search and realtime`() = runBlocking<Unit> {
        for (message in listOf(null, "", "   ")) {
            val extra = message?.let { ""","message":"$it"""" }.orEmpty()
            for ((code, wording) in listOf(
                "agreement_inactive" to "agreement is not active",
                "planning_limit_reached" to "trip planning limit reached",
            )) {
                val reply = Reply(403, "application/json", """{"error":"$code"$extra}""")
                gateway.replies = mapOf(
                    "/stops/v1/search" to reply,
                    "/realtime/v1/alerts" to reply,
                    "/realtime/v1/vehicles/by-trip/T1" to Reply(404, "application/json", """{"error":"$code"$extra}"""),
                )

                val errors = listOf(
                    assertIs<SpiderResult.Error>(stops.search { filter { name eq "Náměstí" } }).error,
                    assertIs<SpiderResult.Error>(realtime.alerts()).error,
                    assertIs<SpiderResult.Error>(realtime.vehicleForTrip("T1")).error,
                )

                for (error in errors) {
                    assertEquals(code, error.code.wireName)
                    assertEquals(wording, error.message)
                }
            }
        }
    }

    @Test
    fun `a plan limit code in the body code field decides on stop search and realtime too`() = runBlocking<Unit> {
        val codeOnly = Reply(403, "application/json", """{"code":"agreement_inactive","message":"agreement is not active"}""")
        gateway.replies = mapOf("/stops/v1/search" to codeOnly, "/realtime/v1/alerts" to codeOnly)

        val errors = listOf(
            assertIs<SpiderResult.Error>(stops.search { filter { name eq "Náměstí" } }).error,
            assertIs<SpiderResult.Error>(realtime.alerts()).error,
        )

        for (error in errors) {
            assertIs<SpiderError.AgreementInactive>(error)
            assertEquals(403, error.httpStatus)
            assertEquals("agreement_inactive", error.serverCode)
        }
    }

    @Test
    fun `a plain 403 on stop search and realtime stays Unauthorized`() = runBlocking<Unit> {
        val plain403 = Reply(403, "application/json", """{"message":"Access to this API has been disallowed"}""")
        gateway.replies = mapOf("/stops/v1/search" to plain403, "/realtime/v1/alerts" to plain403)

        val errors = listOf(
            assertIs<SpiderResult.Error>(stops.search { filter { name eq "Náměstí" } }).error,
            assertIs<SpiderResult.Error>(realtime.alerts()).error,
        )

        for (error in errors) {
            assertIs<SpiderError.Unauthorized>(error)
            assertEquals(403, error.httpStatus)
            assertEquals(null, error.serverCode)
        }
    }

    @Test
    fun `a stop search limit outside 1 to 50 is a BadRequest without a request`() = runBlocking<Unit> {
        assertOutOfRange("limit", stops.search { filter { name eq "x" }; limit = 0 })
        assertOutOfRange("limit", stops.search { filter { name eq "x" }; limit = 51 })
        assertOutOfRange("limit", stops.near(49.19, 16.61, limit = 51))
        assertOutOfRange("limit", stops.within(49.18, 16.59, 49.21, 16.63, limit = 0))
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `no vehicle trip ids is an empty result without a request`() = runBlocking<Unit> {
        val vehicles = assertIs<SpiderResult.Success<VehiclePositions>>(realtime.vehicles(emptyList())).data

        assertEquals(emptyList(), vehicles.vehicles)
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `delays with no ids or a blank id is a BadRequest without a request`() = runBlocking<Unit> {
        assertBadRequest("tripIds is required", realtime.delays("2026-09-28", emptyList()))
        assertBadRequest("tripIds is invalid", realtime.delays("2026-09-28", listOf("1:T1", " ")))
        assertBadRequest("tripIds is invalid", realtime.delays("2026-09-28", listOf("")))
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `more than 50 realtime trip ids is a BadRequest without a request`() = runBlocking<Unit> {
        val ids = (1..51).map { "1:T$it" }
        assertOutOfRange("tripIds", realtime.vehicles(ids))
        assertOutOfRange("tripIds", realtime.delays("2026-09-28", ids))
        assertEquals(emptyList(), gateway.seen.toList())

        gateway.replies = mapOf("/realtime/v1/delays" to json("""{"serviceDate":"2026-09-28","delays":[],"missing":[]}"""))
        assertIs<SpiderResult.Success<TripDelays>>(realtime.delays("2026-09-28", ids.take(50) + ids.take(50)))
    }

    @Test
    fun `delays is one GET with distinct sorted prefixed ids and maps the flat response`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/realtime/v1/delays" to json(
                """
                {"serviceDate":"2026-09-28",
                 "delays":[{"tripId":"1:T2","routeId":"1:R1","delaySeconds":90,"scheduleRelationship":"SCHEDULED",
                   "stopTimeUpdates":[{"stopId":"1:S1","departureDelay":90}]}],
                 "missing":["1:T10"],
                 "feedTimestamp":1721385600,"staleSeconds":4}
                """.trimIndent(),
            ),
        )

        val delays = assertIs<SpiderResult.Success<TripDelays>>(
            realtime.delays("2026-09-28", listOf("1:T2", "1:T10", "1:T2", "1:T1")),
        ).data

        val request = gateway.seen.single()
        assertEquals("GET", request.method)
        assertEquals("serviceDate=2026-09-28&tripIds=1%3AT1%2C1%3AT10%2C1%3AT2", request.query)
        assertEquals("2026-09-28", delays.serviceDate)
        assertEquals(listOf("1:T10"), delays.missing)
        val delay = assertIs<TripDelay>(delays.delayFor("1:T2"))
        assertEquals(90, delay.delaySeconds)
        assertEquals("1:S1", delay.stopTimeUpdates.single().stopId)
        assertEquals(null, delays.delayFor("1:T10"))
        assertEquals(4, delays.freshness.staleSeconds)
    }
}
