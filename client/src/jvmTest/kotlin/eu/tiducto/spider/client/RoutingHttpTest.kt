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
            "\"start\":\"t1\",\"end\":\"t2\",\"waitingTime\":0," +
            "\"duration\":600,\"legs\":[{\"mode\":\"TRAM\",\"start\":{\"scheduledTime\":\"t1\"}," +
            "\"end\":{\"scheduledTime\":\"t2\"},\"realtimeState\":\"SCHEDULED\",\"realTime\":false," +
            "\"distance\":1000.0,\"duration\":600,\"pickupType\":\"SCHEDULED\",\"dropoffType\":\"SCHEDULED\",\"interlineWithPreviousLeg\":false,\"legGeometry\":{\"points\":\"\"}," +
            "\"from\":{\"name\":\"A\"},\"to\":{\"name\":\"B\"}}]}]}\n\n"

    private val emptyPlan = json(
        """{"itineraries":[],"pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
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
            "/routing/v1/plan" to json(
                """
                {"itineraries":[{"start":"t1","end":"t2","numberOfTransfers":0,"duration":600,"waitingTime":0,"legs":[
                  {"mode":"TRAM","start":{"scheduledTime":"t1"},"end":{"scheduledTime":"t2"},
                   "from":{"name":"A","stopPosition":2},"to":{"name":"B","stopPosition":5},
                   "realtimeState":"SCHEDULED","realTime":false,"distance":1000.0,"duration":600,
                   "pickupType":"COORDINATE_WITH_DRIVER","dropoffType":"NONE","interlineWithPreviousLeg":false,"legGeometry":{"points":""}}
                ]}],
                "pageInfo":{"hasNextPage":true,"hasPreviousPage":false,"startCursor":"c-prev","endCursor":"c-next","searchWindowUsed":"PT1H"},
                "routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}
                """.trimIndent(),
            ),
        )

        val route = assertIs<SpiderResult.Success<Route>>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).data

        assertEquals("/routing/v1/plan", gateway.seen.single().path)
        val body = gateway.body()
        assertEquals(setOf("dateTime", "origin", "destination", "searchWindow"), body.keys)
        val edge = route.edges.single()
        assertEquals("NoCursor", edge.cursor)
        assertEquals(TransitMode.TRAM, edge.itinerary.legs.single().mode)
        assertEquals(2 to 5, edge.itinerary.legs.single().let { it.fromStopPosition to it.toStopPosition })
        assertEquals(
            PickupDropoffType.COORDINATE_WITH_DRIVER to PickupDropoffType.NONE,
            edge.itinerary.legs.single().let { it.pickupType to it.dropoffType },
        )
        assertEquals(null, edge.itinerary.accessibilityScore)
        assertEquals("c-next", route.pageInfo.endCursor)
        assertEquals("2026-10-07T08:00:00+02:00", route.searchDateTime)
    }

    @Test
    fun `plan, departures and trip map the run's vehicle and its no-information access to null`() = runBlocking<Unit> {
        val vehicle =
            """{"id":"1:7103","label":"7103","wheelchairAccessible":"POSSIBLE","airConditioning":true,"wifi":false,"cardPayment":null}"""
        val unknown =
            """{"id":null,"label":"2210","wheelchairAccessible":"NO_INFORMATION","airConditioning":null,"wifi":null,"cardPayment":true}"""
        fun leg(mode: String, vehicle: String) =
            """{"mode":"$mode","start":{"scheduledTime":"t1"},"end":{"scheduledTime":"t2"},"from":{"name":"A"},"to":{"name":"B"},
               "realtimeState":"SCHEDULED","realTime":false,"distance":1000.0,"duration":600,"pickupType":"SCHEDULED",
               "dropoffType":"SCHEDULED","interlineWithPreviousLeg":false,"legGeometry":{"points":""},"vehicle":$vehicle}"""
        fun row(vehicle: String) =
            """{"serviceDay":1790546400,"serviceDate":"2026-09-28","scheduledDeparture":81000,"realtimeDeparture":81000,
               "realtime":false,"realtimeState":"SCHEDULED","pickupType":"SCHEDULED","dropoffType":"SCHEDULED",
               "stop":{"gtfsId":"1:S1"},"trip":{"gtfsId":"1:T44","wheelchairAccessible":"POSSIBLE","bikesAllowed":"NO_INFORMATION",
               "route":{"gtfsId":"1:L44","mode":"BUS"}},"vehicle":$vehicle}"""
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[{"start":"t1","end":"t2","numberOfTransfers":0,"duration":1200,"waitingTime":0,
                   "legs":[${leg("WALK", "null")},${leg("TRAM", vehicle)},${leg("BUS", unknown)}]}],
                   "pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
            "/routing/v1/departures" to json(
                """{"stop":{"gtfsId":"1:S","name":"S","stoptimesWithoutPatterns":[${row(vehicle)},${row("null")}]}}""",
            ),
            "/routing/v1/trip" to json(
                """{"trip":{"gtfsId":"1:T44","wheelchairAccessible":"NOT_POSSIBLE","bikesAllowed":"NO_INFORMATION",
                   "route":{"gtfsId":"1:L44","mode":"BUS"},"stoptimesForDate":[],"vehicle":$unknown}}""",
            ),
        )
        val expected = TripVehicle("1:7103", "7103", WheelchairBoarding.POSSIBLE, airConditioning = true, wifi = false, cardPayment = null)
        val noInformation = TripVehicle(null, "2210", null, null, null, cardPayment = true)

        val legs = assertIs<SpiderResult.Success<Route>>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B")))
            .data.edges.single().itinerary.legs
        val departures = assertIs<SpiderResult.Success<List<Departure>>>(routing.departures("1:S")).data
        val trip = assertIs<SpiderResult.Success<TripDetails>>(routing.trip("1:T44", "2026-09-28")).data

        assertEquals(listOf(null, expected, noInformation), legs.map { it.vehicle })
        assertEquals(listOf(expected, null), departures.map { it.vehicle })
        assertEquals(noInformation, trip.vehicle)
        assertEquals(WheelchairBoarding.NOT_POSSIBLE, trip.wheelchairAccessible)
    }

    @Test
    fun `plan sends searchWindow as given`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/v1/plan" to emptyPlan)

        routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), searchWindow = 90.seconds)
        routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))

        assertEquals(listOf("PT1M30S", "PT1H"), bodies().map { it.getValue("searchWindow").jsonPrimitive.content })
    }

    @Test
    fun `planNext and planPrevious send the original body plus one cursor`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":true,"hasPreviousPage":true,"startCursor":"c-prev","endCursor":"c-next"},"routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
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
        gateway.replies = mapOf("/routing/v1/plan" to emptyPlan, "/routing/v1/plan-stream" to emptyDone)

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
    fun `transitOnly rides every plan call and every continuation`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":true,"hasPreviousPage":true,"startCursor":"c-prev","endCursor":"c-next"},"routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
            "/routing/v1/plan-stream" to emptyDone,
        )
        val (a, b) = Location.Stop("1:A") to Location.Stop("1:B")

        val first = assertIs<SpiderResult.Success<Route>>(routing.plan(a, b, transitOnly = true)).data
        routing.planNext(first)
        routing.planPrevious(first)
        routing.planStream(a, b, targetResults = 5, maxWindow = 2.hours, transitOnly = true).toList()
        routing.planStreamNext(a, b, targetResults = 5, maxWindow = 2.hours, after = "c-next", transitOnly = true).toList()
        routing.planStreamPrevious(a, b, targetResults = 5, maxWindow = 2.hours, before = "c-prev", transitOnly = true).toList()
        routing.plan(a, b)
        stream().toList()

        val expected = Json.parseToJsonElement("""{"transitOnly":true}""")
        assertEquals(List(6) { expected } + null + null, bodies().map { it["modes"] })
    }

    @Test
    fun `numberOfItineraries sizes plan as first, planNext as first and planPrevious as last`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":true,"hasPreviousPage":true,"startCursor":"c-prev","endCursor":"c-next"},"routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
        )
        val (a, b) = Location.Stop("1:A") to Location.Stop("1:B")

        val sized = assertIs<SpiderResult.Success<Route>>(routing.plan(a, b, numberOfItineraries = 3)).data
        routing.planNext(sized)
        routing.planPrevious(sized)
        val next = assertIs<SpiderResult.Success<Route>>(routing.planNext(sized, numberOfItineraries = 1)).data
        routing.planPrevious(sized, numberOfItineraries = null)
        routing.planNext(next)
        routing.plan(a, b)

        fun JsonObject.count(key: String) = this[key]?.jsonPrimitive?.int
        assertEquals(
            listOf(3 to null, 3 to null, null to 3, 1 to null, null to null, 1 to null, null to null),
            bodies().map { it.count("first") to it.count("last") },
        )
        assertEquals(listOf(null, "c-next", null, "c-next", null, "c-next", null), bodies().map { it["after"]?.jsonPrimitive?.content })
    }

    @Test
    fun `preferences ride every plan call and every continuation`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":true,"hasPreviousPage":true,"startCursor":"c-prev","endCursor":"c-next"},"routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
            "/routing/v1/plan-stream" to emptyDone,
        )
        val preferences = RoutePreferences(walkSpeed = 1.0, transferSlack = 3.minutes, excludedRoutes = setOf("1:L4"))
        val (a, b) = Location.Stop("1:A") to Location.Stop("1:B")

        val first = assertIs<SpiderResult.Success<Route>>(routing.plan(a, b, preferences = preferences)).data
        routing.planNext(first)
        routing.planPrevious(first)
        routing.planStream(a, b, targetResults = 5, maxWindow = 2.hours, preferences = preferences).toList()
        routing.planStreamNext(a, b, targetResults = 5, maxWindow = 2.hours, after = "c-next", preferences = preferences).toList()
        routing.planStreamPrevious(a, b, targetResults = 5, maxWindow = 2.hours, before = "c-prev", preferences = preferences).toList()
        routing.plan(a, b)

        val expected = Json.parseToJsonElement(
            """{"street":{"walk":{"speed":1.0}},"transit":{"transfer":{"slack":"PT3M"},"filters":[{"exclude":[{"routes":["1:L4"]}]}]}}""",
        )
        assertEquals(List(6) { expected } + null, bodies().map { it["preferences"] })
    }

    @Test
    fun `mode reluctance rides every plan call and every continuation`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":true,"hasPreviousPage":true,"startCursor":"c-prev","endCursor":"c-next"},"routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
            "/routing/v1/plan-stream" to emptyDone,
        )
        val preferences = RoutePreferences(modeReluctance = mapOf(TransitMode.TRAM to 1.0, TransitMode.BUS to 1.5))
        val excluded = setOf(TransitMode.RAIL)
        val (a, b) = Location.Stop("1:A") to Location.Stop("1:B")

        val first = assertIs<SpiderResult.Success<Route>>(
            routing.plan(a, b, excludedTransitModes = excluded, preferences = preferences),
        ).data
        routing.planNext(first)
        routing.planPrevious(first)
        routing.planStream(a, b, targetResults = 5, maxWindow = 2.hours, excludedTransitModes = excluded, preferences = preferences).toList()
        routing.planStreamNext(
            a, b, targetResults = 5, maxWindow = 2.hours, after = "c-next", excludedTransitModes = excluded, preferences = preferences,
        ).toList()
        routing.planStreamPrevious(
            a, b, targetResults = 5, maxWindow = 2.hours, before = "c-prev", excludedTransitModes = excluded, preferences = preferences,
        ).toList()
        routing.plan(a, b)

        val expected = Json.parseToJsonElement(
            """{"transit":{"exclude":["RAIL"],"costs":[{"mode":"BUS","reluctance":1.5},{"mode":"TRAM","reluctance":1.0}]}}""",
        )
        assertEquals(List(6) { expected } + null, bodies().map { it["modes"] })
        assertEquals(List(7) { null }, bodies().map { it["preferences"] })
    }

    @Test
    fun `a mode reluctance out of range fails plan and planStream without a request`() = runBlocking<Unit> {
        val preferences = RoutePreferences(modeReluctance = mapOf(TransitMode.TRAM to 0.99))

        val plan = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), preferences = preferences))
        val stream = assertIs<PlanStreamEvent.Failure>(
            routing.planStream(
                Location.Stop("1:A"), Location.Stop("1:B"), targetResults = 5, maxWindow = 2.hours, preferences = preferences,
            ).toList().single(),
        )

        assertBadRequest("modes.transit.costs.reluctance", plan.error)
        assertBadRequest("modes.transit.costs.reluctance", stream.error)
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `plan and planStream legs carry the parent stations of their stops`() = runBlocking<Unit> {
        val leg =
            """{"mode":"TRAM","start":{"scheduledTime":"t1"},"end":{"scheduledTime":"t2"},
               "from":{"name":"Česká","stop":{"gtfsId":"1:U1Z1","wheelchairBoarding":"POSSIBLE","parentStation":{"gtfsId":"1:U1","name":"Česká"}}},
               "to":{"name":"Hlavní nádraží","stop":{"gtfsId":"1:U2Z3","wheelchairBoarding":"POSSIBLE","parentStation":null}},
               "realtimeState":"SCHEDULED","realTime":false,"distance":1000.0,"duration":600,"pickupType":"SCHEDULED",
               "dropoffType":"SCHEDULED","interlineWithPreviousLeg":false,"legGeometry":{"points":""}}"""
        val walk =
            """{"mode":"WALK","start":{"scheduledTime":"t2"},"end":{"scheduledTime":"t3"},"from":{"name":"Hlavní nádraží",
               "stop":{"gtfsId":"1:U2Z3","wheelchairBoarding":"POSSIBLE"}},"to":{"name":"Destination"},
               "realtimeState":"SCHEDULED","realTime":false,"distance":100.0,"duration":60,"pickupType":"SCHEDULED",
               "dropoffType":"SCHEDULED","interlineWithPreviousLeg":false,"legGeometry":{"points":""}}"""
        val itinerary = """{"start":"t1","end":"t3","numberOfTransfers":0,"duration":660,"waitingTime":0,"legs":[$leg,$walk]}"""
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[$itinerary],"pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":[],
                   "searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
            "/routing/v1/plan-stream" to events(
                "event: chunk\ndata: {\"frontier\":600,\"found\":1,\"finalized\":1,\"results\":[" +
                    itinerary.replace("\n", " ").replace(Regex("\\s+"), " ") + "]}\n\n" + pageInfoEvent + doneEvent,
            ),
        )
        val (a, b) = Location.Stop("1:U1") to Location.Coordinate(49.19, 16.61)

        val planned = assertIs<SpiderResult.Success<Route>>(routing.plan(a, b)).data.edges.single().itinerary.legs
        val streamed = assertIs<PlanStreamEvent.Result>(
            routing.planStream(a, b, targetResults = 5, maxWindow = 2.hours).toList().first(),
        ).itineraries.single().legs

        val station = ParentStation("1:U1", "Česká")
        for (legs in listOf(planned, streamed)) {
            assertEquals(listOf(station to null, null to null), legs.map { it.fromParentStation to it.toParentStation })
        }
    }

    @Test
    fun `stopDepartures carries the stop with its parent station and a station's without one`() = runBlocking<Unit> {
        val row =
            """{"serviceDay":1790546400,"serviceDate":"2026-09-28","scheduledDeparture":81000,"realtimeDeparture":81000,
               "realtime":false,"realtimeState":"SCHEDULED","pickupType":"SCHEDULED","dropoffType":"SCHEDULED",
               "stop":{"gtfsId":"1:U1Z1","platformCode":"1"},"trip":{"gtfsId":"1:T44","wheelchairAccessible":"POSSIBLE",
               "bikesAllowed":"NO_INFORMATION","route":{"gtfsId":"1:L44","mode":"BUS"}}}"""
        gateway.replies = mapOf(
            "/routing/v1/departures" to json(
                """{"stop":{"gtfsId":"1:U1Z1","name":"Česká","wheelchairBoarding":"POSSIBLE",
                   "parentStation":{"gtfsId":"1:U1","name":"Česká"},"stoptimesWithoutPatterns":[$row]}}""",
            ),
        )

        val stop = assertIs<SpiderResult.Success<StopDepartures>>(routing.stopDepartures("1:U1Z1")).data
        val departures = assertIs<SpiderResult.Success<List<Departure>>>(routing.departures("1:U1Z1")).data

        assertEquals("1:U1Z1" to "Česká", stop.gtfsId to stop.name)
        assertEquals(WheelchairBoarding.POSSIBLE, stop.wheelchairBoarding)
        assertEquals(ParentStation("1:U1", "Česká"), stop.parentStation)
        assertEquals(listOf("1:T44"), stop.departures.map { it.tripGtfsId })
        assertEquals(stop.departures, departures)
        assertEquals(1, gateway.seen.map { it.body }.distinct().size)

        gateway.replies = mapOf(
            "/routing/v1/departures" to json(
                """{"stop":{"gtfsId":"1:U1","name":"Česká","wheelchairBoarding":null,"parentStation":null,"stoptimesWithoutPatterns":[$row]}}""",
            ),
        )
        val station = assertIs<SpiderResult.Success<StopDepartures>>(routing.stopDepartures("1:U1")).data
        assertEquals("1:U1", station.gtfsId)
        assertEquals(null, station.wheelchairBoarding)
        assertEquals(null, station.parentStation)
        assertEquals(listOf("1:U1Z1"), station.departures.map { it.stopGtfsId })
    }

    @Test
    fun `a preference out of range fails plan and planStream without a request`() = runBlocking<Unit> {
        val preferences = RoutePreferences(boardSlack = 2.hours)

        val plan = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), preferences = preferences))
        val stream = assertIs<PlanStreamEvent.Failure>(
            routing.planStream(
                Location.Stop("1:A"), Location.Stop("1:B"), targetResults = 5, maxWindow = 2.hours, preferences = preferences,
            ).toList().single(),
        )

        assertBadRequest("preferences.transit.board.slack", plan.error)
        assertBadRequest("preferences.transit.board.slack", stream.error)
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `excluded transit modes ride every plan call and every continuation`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":true,"hasPreviousPage":true,"startCursor":"c-prev","endCursor":"c-next"},"routingErrors":[],"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
            "/routing/v1/plan-stream" to emptyDone,
        )
        val excluded = setOf(TransitMode.TRAM, TransitMode.UNKNOWN)
        val (a, b) = Location.Stop("1:A") to Location.Stop("1:B")

        val first = assertIs<SpiderResult.Success<Route>>(routing.plan(a, b, excludedTransitModes = excluded)).data
        routing.planNext(first)
        routing.planPrevious(first)
        routing.planStream(a, b, targetResults = 5, maxWindow = 2.hours, excludedTransitModes = excluded).toList()
        routing.planStreamNext(a, b, targetResults = 5, maxWindow = 2.hours, after = "c-next", excludedTransitModes = excluded).toList()
        routing.planStreamPrevious(a, b, targetResults = 5, maxWindow = 2.hours, before = "c-prev", excludedTransitModes = excluded).toList()
        routing.plan(a, b, excludedTransitModes = emptySet())

        val modes = Json.parseToJsonElement("""{"transit":{"exclude":["TRAM"]}}""")
        assertEquals(List(6) { modes } + listOf(null), bodies().map { it["modes"] })
    }

    @Test
    fun `an allow-list with an exclusion is a BadRequest without a request on plan and planStream`() = runBlocking<Unit> {
        val allowed = setOf(TransitMode.BUS)
        val excluded = setOf(TransitMode.TRAM)
        val (a, b) = Location.Stop("1:A") to Location.Stop("1:B")

        val planError = assertIs<SpiderResult.Error>(
            routing.plan(a, b, allowedTransitModes = allowed, excludedTransitModes = excluded),
        ).error
        val streamError = assertIs<PlanStreamEvent.Failure>(
            routing.planStream(
                a, b, allowedTransitModes = allowed, targetResults = 5, maxWindow = 2.hours, excludedTransitModes = excluded,
            ).toList().single(),
        ).error

        for (error in listOf(planError, streamError)) {
            assertBadRequest("modes.transit", error, state = "invalid")
            assertEquals(null, error.httpStatus)
        }
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `departures send excludeModes only when a transit mode is excluded`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/departures" to json("""{"stop":{"gtfsId":"1:S","name":"S","stoptimesWithoutPatterns":[]}}"""),
        )

        routing.departures("1:S", excludedModes = setOf(TransitMode.TRAM, TransitMode.WALK, TransitMode.TROLLEYBUS))
        routing.departures("1:S", excludedModes = emptySet())
        routing.departures("1:S", excludedModes = setOf(TransitMode.UNKNOWN))
        routing.departures("1:S")

        val plain = """{"id":"1:S","numberOfDepartures":30,"timeRange":86400}"""
        assertEquals(
            listOf(
                """{"id":"1:S","numberOfDepartures":30,"timeRange":86400,"excludeModes":["TRAM","TROLLEYBUS"]}""",
                plain,
                plain,
                plain,
            ).map(Json::parseToJsonElement),
            bodies(),
        )
    }

    @Test
    fun `a routing HTTP 400 is a BadRequest carrying the body field`() = runBlocking<Unit> {
        val field = "preferences.transit.transfer.maximumTransfers"
        val invalid = Reply(400, "application/json", """{"code":"bad_request","message":"$field is out of range","field":"$field"}""")
        gateway.replies = mapOf(
            "/routing/v1/plan" to invalid,
            "/routing/v1/plan-stream" to invalid,
            "/routing/v1/departures" to Reply(400, "application/json", """{"code":"bad_request","message":"id is required","field":"id"}"""),
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
            "/routing/v1/plan" to Reply(400, "application/json", """{"code":"bad_request","message":"via.visit.coordinate is not allowed"}"""),
        )

        val error = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).error

        assertBadRequest("via.visit.coordinate", error, state = "not allowed")
    }

    @Test
    fun `planStream posts the body with an event-stream Accept and always sends targetResults and maxWindow`() =
        runBlocking<Unit> {
            gateway.replies = mapOf("/routing/v1/plan-stream" to emptyDone)

            assertIs<PlanStreamEvent.Done>(stream(maxWindow = 2.hours, targetResults = 3).toList().single())

            val seen = gateway.seen.single()
            assertEquals("/routing/v1/plan-stream", seen.path)
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
        gateway.replies = mapOf("/routing/v1/plan-stream" to events(chunkEvent + chunkEvent + pageInfoEvent + doneEvent))

        val received = stream().toList()

        assertEquals(3, received.size)
        assertIs<PlanStreamEvent.Result>(received[0])
        assertIs<PlanStreamEvent.Result>(received[1])
        assertIs<PlanStreamEvent.Done>(received[2])
    }

    @Test
    fun `a stream skips event names it does not know`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan-stream" to events(
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
    fun `a stream cut before pageInfo ends in a Network Failure`() = runBlocking<Unit> {
        for (cut in listOf("", chunkEvent, "event: error\ndata: {\"message\":\"boom\"}\n\n")) {
            gateway.replies = mapOf("/routing/v1/plan-stream" to events(cut))

            val received = stream().toList()

            val failure = assertIs<PlanStreamEvent.Failure>(received.last())
            assertIs<SpiderError.Network>(failure.error)
            assertEquals(received.dropLast(1), received.filterIsInstance<PlanStreamEvent.Result>())
        }
    }

    @Test
    fun `stream routing errors arrive on Done`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan-stream" to events(
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
            "/routing/v1/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":$viaNotFound,"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
            "/routing/v1/plan-stream" to events(
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
    fun `contract 1_5 routing error codes decode on plan and planStream`() = runBlocking<Unit> {
        val errors = """[
            {"code":"NO_DIRECT_MODE_CONNECTION","description":"no walk","inputField":null},
            {"code":"NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW","description":"nothing in the window","inputField":"DATE_TIME"},
            {"code":"OUTSIDE_BOUNDS","description":"outside the area","inputField":"TO"}
        ]"""
        gateway.replies = mapOf(
            "/routing/v1/plan" to json(
                """{"itineraries":[],"pageInfo":{"hasNextPage":false,"hasPreviousPage":false},"routingErrors":$errors,"searchDateTime":"2026-10-07T08:00:00+02:00"}""",
            ),
            "/routing/v1/plan-stream" to events(
                "event: pageInfo\ndata: {\"hasNextPage\":false,\"hasPreviousPage\":false,\"routingErrors\":" +
                    errors.replace("\n", "") + "}\n\n" + doneEvent,
            ),
        )

        val route = assertIs<SpiderResult.Success<Route>>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"))).data
        val done = assertIs<PlanStreamEvent.Done>(stream().toList().single())

        val expected = listOf(
            RoutingError(RoutingErrorCode.NO_DIRECT_MODE_CONNECTION, "no walk", null),
            RoutingError(RoutingErrorCode.NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW, "nothing in the window", InputField.DATE_TIME),
            RoutingError(RoutingErrorCode.OUTSIDE_BOUNDS, "outside the area", InputField.TO),
        )
        assertEquals(expected, route.routingErrors)
        assertEquals(expected, done.routingErrors)
    }

    @Test
    fun `mixed visit and passThrough via locations are sent in order on plan and planStream`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/v1/plan" to emptyPlan, "/routing/v1/plan-stream" to emptyDone)
        val via = listOf(
            ViaLocation.Visit(Location.Stop("1:V"), minimumWaitTime = 5.minutes),
            ViaLocation.PassThrough(listOf("1:P1", "1:P2")),
            ViaLocation.Visit(Location.Stop("1:W")),
        )

        assertIs<SpiderResult.Success<Route>>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), via = via))
        assertIs<PlanStreamEvent.Done>(stream(via = via).toList().single())

        val expected = Json.parseToJsonElement(
            """[
              {"visit":{"stopLocationIds":["1:V"],"minimumWaitTime":"PT5M"}},
              {"passThrough":{"stopLocationIds":["1:P1","1:P2"]}},
              {"visit":{"stopLocationIds":["1:W"]}}
            ]""",
        )
        assertEquals(listOf(expected, expected), bodies().map { it.getValue("via") })
    }

    @Test
    fun `via limits are checked before any request on plan and planStream`() = runBlocking<Unit> {
        val wait = "via.visit.minimumWaitTime"
        val invalid = listOf(
            ViaLocation.PassThrough(emptyList()) to "via",
            ViaLocation.PassThrough((1..11).map { "1:S$it" }) to "via",
            ViaLocation.Visit(Location.Stop("1:V"), minimumWaitTime = (-1).seconds) to wait,
            ViaLocation.Visit(Location.Stop("1:V"), minimumWaitTime = 1.hours + 1.seconds) to wait,
        )
        for ((via, field) in invalid) {
            val planError = assertIs<SpiderResult.Error>(routing.plan(Location.Stop("1:A"), Location.Stop("1:B"), via = listOf(via))).error
            assertBadRequest(field, planError)
            assertBadRequest(field, assertIs<PlanStreamEvent.Failure>(stream(via = listOf(via)).toList().single()).error)
        }
        assertEquals(emptyList(), gateway.seen.toList())

        gateway.replies = mapOf("/routing/v1/plan" to emptyPlan)
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
    fun `a plain 403 stays a key problem`() = runBlocking<Unit> {
        val plain403 = Reply(403, "application/json", """{"message":"Access to this API has been disallowed"}""")
        gateway.replies = mapOf("/routing/v1/plan" to plain403, "/routing/v1/plan-stream" to plain403)

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
        gateway.replies = mapOf("/routing/v1/plan" to planningLimit(), "/routing/v1/plan-stream" to planningLimit())

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
        gateway.replies = listOf("/routing/v1/plan", "/routing/v1/plan-stream", "/routing/v1/departures", "/routing/v1/trip")
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
                "/routing/v1/plan" to limited,
                "/routing/v1/plan-stream" to limited,
                "/routing/v1/departures" to inactive,
                "/routing/v1/trip" to inactive,
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
        gateway.replies = mapOf("/routing/v1/plan" to codeOnly, "/routing/v1/plan-stream" to codeOnly)

        for (error in listOf(planError(), streamError())) {
            assertIs<SpiderError.AgreementInactive>(error)
            assertEquals(403, error.httpStatus)
            assertEquals("agreement_inactive", error.serverCode)
        }
    }

    @Test
    fun `a plan limit code decides over a status a proxy rewrote`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/plan" to planningLimit(400),
            "/routing/v1/plan-stream" to planningLimit(429),
            "/routing/v1/departures" to agreementInactive(429),
            "/routing/v1/trip" to agreementInactive(410),
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
            "/routing/v1/departures" to json(
                """
                {"stop":{"gtfsId":"1:S","name":"Zvonařka","wheelchairBoarding":null,"stoptimesWithoutPatterns":[
                  {"serviceDay":$serviceDay,"serviceDate":"2026-09-28","scheduledDeparture":81000,"realtimeDeparture":81060,"realtime":true,
                   "realtimeState":"UPDATED","headsign":"Zvonařka","typicalDelay":120,"stopPosition":7,
                   "pickupType":"SCHEDULED","dropoffType":"COORDINATE_WITH_DRIVER",
                   "stop":{"gtfsId":"1:S1","platformCode":"B"},
                   "trip":{"gtfsId":"1:T44","wheelchairAccessible":"POSSIBLE","bikesAllowed":"ALLOWED",
                     "route":{"gtfsId":"1:L44","shortName":"44","mode":"BUS","color":"FF0000","textColor":"FFFFFF"}}},
                  {"serviceDay":$serviceDay,"serviceDate":"2026-09-28","scheduledDeparture":88800,"realtimeDeparture":88800,"realtime":false,
                   "realtimeState":"CANCELED","headsign":"Líšeň","stop":{"gtfsId":"1:S2"},
                   "pickupType":"CALL_AGENCY","dropoffType":"SOMETHING_NEW",
                   "trip":{"gtfsId":"1:N89","wheelchairAccessible":"NO_INFORMATION","bikesAllowed":"NO_INFORMATION",
                     "route":{"gtfsId":"1:LN89","shortName":"N89","mode":"BUS"}}}
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
        assertEquals(listOf(null, null, null), listOf(bare.routeColor, bare.routeTextColor, bare.platformCode))
        assertEquals("1:S2", bare.stopGtfsId)
        assertEquals(full.scheduledTime + 1.minutes, full.realtimeTime)
        assertEquals(bare.scheduledTime, bare.realtimeTime)
        assertEquals(null, bare.wheelchairAccessible)
        assertEquals(2.minutes, full.typicalDelay)
        assertEquals(null, bare.typicalDelay)
        assertEquals(7, full.stopPosition)
        assertEquals(null, bare.stopPosition)
        assertEquals(PickupDropoffType.SCHEDULED to PickupDropoffType.COORDINATE_WITH_DRIVER, full.pickupType to full.dropoffType)
        assertEquals(PickupDropoffType.CALL_AGENCY to PickupDropoffType.UNKNOWN, bare.pickupType to bare.dropoffType)
        assertEquals(
            Json.parseToJsonElement("""{"id":"1:S","numberOfDepartures":30,"timeRange":86400}"""),
            gateway.body(),
        )
    }

    @Test
    fun `an unknown stop or trip id is NotFound`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/departures" to json("""{"stop":null}"""),
            "/routing/v1/trip" to json("""{"trip":null}"""),
        )

        assertIs<SpiderError.NotFound>(assertIs<SpiderResult.Error>(routing.departures("1:NOPE")).error)
        assertIs<SpiderError.NotFound>(assertIs<SpiderResult.Error>(routing.stopDepartures("1:NOPE")).error)
        assertIs<SpiderError.NotFound>(assertIs<SpiderResult.Error>(routing.trip("1:NOPE", "2026-09-28")).error)
        assertEquals(listOf("""{"id":"1:NOPE","serviceDate":"2026-09-28"}"""), gateway.seen.filter { it.path == "/routing/v1/trip" }.map { it.body })
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
            "/routing/v1/trip" to json(
                """
                {"trip":{"gtfsId":"1:N89","wheelchairAccessible":"NO_INFORMATION","bikesAllowed":"SOMETHING_NEW",
                  "route":{"gtfsId":"1:LN89","shortName":"N89","mode":"BUS","color":"00AA00","textColor":"000000"},"stoptimesForDate":[
                  {"serviceDay":1790546400,"scheduledArrival":88800,"scheduledDeparture":88800,"realtimeArrival":88800,
                   "realtimeDeparture":88800,"realtime":false,"realtimeState":"SCHEDULED","typicalDelay":45,
                   "stopPosition":3,"geometryIndex":2,"pickupType":"COORDINATE_WITH_DRIVER","dropoffType":"SCHEDULED",
                   "stop":{"gtfsId":"1:U1","name":"Líšeň","lat":49.21,"lon":16.68,"wheelchairBoarding":"POSSIBLE","platformCode":"2","zoneId":"101",
                     "parentStation":{"gtfsId":"1:U","name":"Líšeň"}}},
                  {"serviceDay":1790546400,"scheduledArrival":89400,"scheduledDeparture":89400,"realtimeArrival":89400,
                   "realtimeDeparture":89400,"realtime":false,"realtimeState":"SCHEDULED","stopPosition":5,
                   "pickupType":null,"dropoffType":null,
                   "stop":{"gtfsId":"1:U2","name":"Jírova","lat":49.22,"lon":16.69,"wheelchairBoarding":"NO_INFORMATION"}}
                ],"tripGeometry":{"points":"_izlhA~rlgdF_{geC~ywl@_kwzCn`{nI","length":3}}}
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
        assertEquals(3 to 5, first.stopPosition to second.stopPosition)
        assertEquals(LatLon(43.252, -126.453), trip.geometry[first.geometryIndex!!])
        assertEquals(null, second.geometryIndex)
        assertEquals(PickupDropoffType.COORDINATE_WITH_DRIVER to PickupDropoffType.SCHEDULED, first.pickupType to first.dropoffType)
        assertEquals(null to null, second.pickupType to second.dropoffType)
        assertEquals(ParentStation("1:U", "Líšeň"), first.parentStation)
        assertEquals(null, second.parentStation)
    }

    @Test
    fun `a malformed service date is a BadRequest without a request`() = runBlocking<Unit> {
        val tripError = assertIs<SpiderResult.Error>(routing.trip("1:T", "20260928")).error
        val estimatedError = assertIs<SpiderResult.Error>(routing.estimatedDelays("1:T", "2026-9-28", 3)).error
        val delaysError = assertIs<SpiderResult.Error>(
            SpiderRealtime(gateway.baseUrl, "test-key").tripUpdates("2026-02-30", listOf("1:T")),
        ).error

        for (error in listOf(tripError, estimatedError, delaysError)) {
            val badRequest = assertIs<SpiderError.BadRequest>(error)
            assertEquals("serviceDate", badRequest.field)
            assertEquals("serviceDate is invalid", badRequest.message)
        }
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `departures and trip send reliability only when it is set`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/departures" to json("""{"stop":{"gtfsId":"1:S","name":"S","stoptimesWithoutPatterns":[]}}"""),
            "/routing/v1/trip" to json("""{"trip":null}"""),
        )

        routing.departures("1:S", reliability = Reliability.SAFE)
        routing.departures("1:S")
        routing.trip("1:T", "2026-09-28", reliability = Reliability.VERY_SAFE)
        routing.trip("1:T", "2026-09-28")

        assertEquals(
            listOf(
                """{"id":"1:S","numberOfDepartures":30,"timeRange":86400,"reliability":"SAFE"}""",
                """{"id":"1:S","numberOfDepartures":30,"timeRange":86400}""",
                """{"id":"1:T","serviceDate":"2026-09-28","reliability":"VERY_SAFE"}""",
                """{"id":"1:T","serviceDate":"2026-09-28"}""",
            ).map(Json::parseToJsonElement),
            bodies(),
        )
    }

    @Test
    fun `estimatedDelays is one GET for the trip run's stop and maps the levels by name`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/estimated-delays" to json(
                """
                {"estimatedDelays":[{"reliability":"VERY_SAFE","delay":300},{"reliability":"STANDARD","delay":0},
                  {"reliability":"SAFE","delay":90}]}
                """.trimIndent(),
            ),
        )

        val delays = assertIs<SpiderResult.Success<EstimatedDelays?>>(routing.estimatedDelays("1:T 7", "2026-09-28", 3)).data

        assertEquals(EstimatedDelays(standard = Duration.ZERO, safe = 90.seconds, verySafe = 5.minutes), delays)
        val request = gateway.seen.single()
        assertEquals("GET", request.method)
        assertEquals("tripId=1%3AT+7&serviceDate=2026-09-28&stopPosition=3", request.query)
    }

    @Test
    fun `trip and estimatedDelays send the run's start time only when it is set`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/trip" to json("""{"trip":null}"""),
            "/routing/v1/estimated-delays" to json("""{"estimatedDelays":null}"""),
        )

        routing.trip("1:T", "2026-09-28", startTime = "8:10:00")
        routing.trip("1:T", "2026-09-28", startTime = "")
        routing.estimatedDelays("1:T", "2026-09-28", 3, startTime = "25:10:00")
        routing.estimatedDelays("1:T", "2026-09-28", 3)
        routing.estimatedDelays("1:T", "2026-09-28", 3, startTime = "")

        assertEquals(
            listOf(
                """{"id":"1:T","serviceDate":"2026-09-28","startTime":"8:10:00"}""",
                """{"id":"1:T","serviceDate":"2026-09-28"}""",
            ).map(Json::parseToJsonElement),
            gateway.seen.take(2).map { Json.parseToJsonElement(it.body) },
        )
        assertEquals(
            listOf(
                "tripId=1%3AT&serviceDate=2026-09-28&stopPosition=3&startTime=25%3A10%3A00",
                "tripId=1%3AT&serviceDate=2026-09-28&stopPosition=3",
                "tripId=1%3AT&serviceDate=2026-09-28&stopPosition=3",
            ),
            gateway.seen.drop(2).map { it.query },
        )
    }

    @Test
    fun `a malformed start time is a BadRequest without a request`() = runBlocking<Unit> {
        for (startTime in listOf(" ", "08:10", "08:10:60", "100:00:00")) {
            assertBadRequest("startTime", assertIs<SpiderResult.Error>(routing.trip("1:T", "2026-09-28", startTime = startTime)).error, "invalid")
            assertBadRequest("startTime", assertIs<SpiderResult.Error>(routing.estimatedDelays("1:T", "2026-09-28", 3, startTime)).error, "invalid")
        }
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `estimatedDelays null is a success without delays`() = runBlocking<Unit> {
        gateway.replies = mapOf("/routing/v1/estimated-delays" to json("""{"estimatedDelays":null}"""))

        assertEquals(SpiderResult.Success(null), routing.estimatedDelays("1:T", "2026-09-28", 3))
    }

    @Test
    fun `estimatedDelays missing or repeating a level is a decoding failure`() = runBlocking<Unit> {
        for (entries in listOf(
            """[{"reliability":"STANDARD","delay":0},{"reliability":"SAFE","delay":90}]""",
            """[{"reliability":"STANDARD","delay":0},{"reliability":"SAFE","delay":90},{"reliability":"SAFE","delay":90}]""",
            """[{"reliability":"STANDARD","delay":0},{"reliability":"SAFE","delay":90},{"reliability":"P99","delay":900}]""",
        )) {
            gateway.replies = mapOf("/routing/v1/estimated-delays" to json("""{"estimatedDelays":$entries}"""))

            val error = assertIs<SpiderResult.Error>(routing.estimatedDelays("1:T", "2026-09-28", 3)).error

            assertIs<SpiderError.Decoding>(error)
        }
    }

    @Test
    fun `estimatedDelays maps routing errors like the other routing calls`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/routing/v1/estimated-delays" to Reply(
                400,
                "application/json",
                """{"code":"bad_request","message":"stopPosition is invalid","field":"stopPosition"}""",
            ),
        )
        assertBadRequest("stopPosition", assertIs<SpiderResult.Error>(routing.estimatedDelays("1:T", "2026-09-28", -1)).error, "invalid")

        gateway.replies = mapOf("/routing/v1/estimated-delays" to agreementInactive())
        assertIs<SpiderError.AgreementInactive>(
            assertIs<SpiderResult.Error>(routing.estimatedDelays("1:T", "2026-09-28", 3)).error,
        )
    }
}
