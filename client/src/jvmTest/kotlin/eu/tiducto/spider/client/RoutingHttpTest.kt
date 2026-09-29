package eu.tiducto.spider.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Drives the public routing/realtime calls end to end against a local JDK HTTP server standing in for the
 * gateway, so request defaults, response mapping and error mapping are checked on the real engine.
 */
class RoutingHttpTest {

    private data class Seen(val path: String, val body: String)

    private class Reply(val status: Int, val contentType: String, val body: String)

    private val seen = CopyOnWriteArrayList<Seen>()
    private var replies: Map<String, Reply> = emptyMap()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            seen += Seen(path, exchange.requestBody.readBytes().decodeToString())
            val reply = replies[path] ?: Reply(404, "application/json", "{}")
            val bytes = reply.body.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", reply.contentType)
            exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    private val baseUrl = "http://127.0.0.1:${server.address.port}"
    private val routing = SpiderRouting(baseUrl, "test-key")

    @AfterTest
    fun stop() = server.stop(0)

    private fun json(body: String) = Reply(200, "application/json", body)

    @Test
    fun `planStream with default arguments sends no maxWindow`() = runBlocking {
        replies = mapOf(
            "/routing/plan-stream" to Reply(
                200,
                "text/event-stream",
                "event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":[]}\n\n",
            ),
        )

        val events = routing.planStream(Location.Stop("1:A"), Location.Stop("1:B")).toList()

        assertIs<PlanStreamEvent.Done>(events.single())
        val variables = Json.parseToJsonElement(seen.single().body).jsonObject.getValue("variables").jsonObject
        assertEquals(null, variables["maxWindow"])
    }

    @Test
    fun `stream routing errors arrive on Done`() = runBlocking {
        replies = mapOf(
            "/routing/plan-stream" to Reply(
                200,
                "text/event-stream",
                "event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":" +
                    "[{\"code\":\"LOCATION_NOT_FOUND\",\"description\":\"no such stop\",\"inputField\":\"FROM\"}]}\n\n",
            ),
        )

        val done = assertIs<PlanStreamEvent.Done>(routing.planStream(Location.Stop("1:X"), Location.Stop("1:B")).toList().single())

        assertEquals(RoutingErrorCode.LOCATION_NOT_FOUND, done.routingErrors.single().code)
        assertEquals(InputField.FROM, done.routingErrors.single().inputField)
    }

    @Test
    fun `a retired query id is an update-the-SDK error on plan and planStream`() = runBlocking {
        val rejected = Reply(
            403,
            "application/json",
            """{"error":"persisted_query_rejected","message":"unknown persisted-query id: abc"}""",
        )
        replies = mapOf("/routing/plan" to rejected, "/routing/plan-stream" to rejected)

        val planError = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error
        val streamEvent = routing.planStream(Location.Stop("1:A"), Location.Stop("1:B")).toList().single()
        val streamError = assertIs<PlanStreamEvent.Failure>(streamEvent).error

        for (error in listOf(planError, streamError)) {
            assertIs<SpiderError.Unauthorized>(error)
            assertEquals(403, error.httpStatus)
            assertEquals(RETIRED_QUERY_SERVER_CODE, error.serverCode)
            assertTrue("update the Spider SDK" in error.message, error.message)
        }
    }

    @Test
    fun `a plain 403 stays a key problem`() = runBlocking {
        replies = mapOf("/routing/plan" to Reply(403, "application/json", """{"message":"Access to this API has been disallowed"}"""))

        val error = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error

        assertIs<SpiderError.Unauthorized>(error)
        assertEquals(null, error.serverCode)
    }

    // A night departure after midnight belongs to the previous service date; rows whose headsign matches the
    // stop name are real departures (the router drops a trip's final stop itself).
    @Test
    fun `departures carry the service date and keep every row`() = runBlocking {
        val serviceDay = 1_790_546_400L // 2026-09-28 in Europe/Prague
        replies = mapOf(
            "/routing/departures" to json(
                """
                {"data":{"asStation":{"gtfsId":"1:S","name":"Zvonařka","stoptimesWithoutPatterns":[
                  {"serviceDay":$serviceDay,"scheduledDeparture":81000,"headsign":"Zvonařka",
                   "trip":{"gtfsId":"1:T44","route":{"shortName":"44","mode":"BUS"}}},
                  {"serviceDay":$serviceDay,"scheduledDeparture":88800,"realtimeState":"CANCELED","headsign":"Líšeň",
                   "trip":{"gtfsId":"1:N89","route":{"shortName":"N89","mode":"BUS"}}}
                ]}}}
                """.trimIndent(),
            ),
        )

        val departures = assertIs<SpiderResult.Success<List<Departure>>>(routing.departures("1:S")).data

        assertEquals(listOf("1:T44", "1:N89"), departures.map { it.tripGtfsId })
        assertEquals(listOf("2026-09-28", "2026-09-28"), departures.map { it.serviceDate })
        assertEquals(RealtimeState.CANCELED, departures[1].realtimeState)
    }

    @Test
    fun `trip reports its service date`() = runBlocking {
        replies = mapOf(
            "/routing/trip" to json(
                """
                {"data":{"trip":{"gtfsId":"1:N89","route":{"shortName":"N89"},"stoptimesForDate":[
                  {"serviceDay":1790546400,"scheduledDeparture":88800,"stop":{"gtfsId":"1:U1","name":"Líšeň"}}
                ]}}}
                """.trimIndent(),
            ),
        )

        val trip = assertIs<SpiderResult.Success<TripDetails>>(routing.trip("1:N89", "2026-09-28")).data

        assertEquals("2026-09-28", trip.serviceDate)
    }

    @Test
    fun `a malformed service date is a BadRequest without a request`() = runBlocking {
        val tripError = assertIs<SpiderResult.Error>(routing.trip("1:T", "20260928")).error
        val delaysError = assertIs<SpiderResult.Error>(
            SpiderRealtime(baseUrl, "test-key").delays(listOf("1:T"), "2026-02-30"),
        ).error

        for (error in listOf(tripError, delaysError)) {
            assertEquals("serviceDate", assertIs<SpiderError.BadRequest>(error).field)
        }
        assertEquals(emptyList(), seen.toList())
    }
}
