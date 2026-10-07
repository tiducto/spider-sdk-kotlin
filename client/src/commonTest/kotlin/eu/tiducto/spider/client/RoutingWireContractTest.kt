package eu.tiducto.spider.client

import eu.tiducto.spider.contract.routing.BikesAllowed
import eu.tiducto.spider.contract.routing.DeparturesRequest
import eu.tiducto.spider.contract.routing.DeparturesResponse
import eu.tiducto.spider.contract.routing.Mode
import eu.tiducto.spider.contract.routing.PlanTripRequest
import eu.tiducto.spider.contract.routing.PlanTripResponse
import eu.tiducto.spider.contract.routing.TripRequest
import eu.tiducto.spider.contract.routing.TripResponse
import eu.tiducto.spider.contract.routing.WheelchairBoarding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The Json config mirrors RoutingClient's exactly; the two must stay in lockstep. */
class RoutingWireContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun encode(body: PlanTripRequest): JsonObject =
        json.encodeToJsonElement(PlanTripRequest.serializer(), body).jsonObject

    private val request = PlanRequest(
        origin = Location.Stop("1:U123"),
        destination = Location.Coordinate(49.2, 16.6),
        time = RouteTime.DepartAt(Instant.parse("2026-07-19T10:00:00Z")),
    )

    @Test
    fun `plan body serializes without nulls or label`() {
        val body = request.copy(via = listOf(ViaLocation.PassThrough("1:U999"))).toPlanTripRequest(before = null, after = null)

        val expected = json.parseToJsonElement(
            """
            {
              "dateTime": { "earliestDeparture": "2026-07-19T10:00:00Z" },
              "origin": { "location": { "stopLocation": { "stopLocationId": "1:U123" } } },
              "destination": { "location": { "coordinate": { "latitude": 49.2, "longitude": 16.6 } } },
              "via": [ { "passThrough": { "stopLocationIds": ["1:U999"] } } ],
              "searchWindow": "PT1H"
            }
            """.trimIndent(),
        )
        assertEquals(expected, encode(body))
    }

    @Test
    fun `PlanRequest maps modes transfers wheelchair reliability and search window`() {
        val body = request.copy(
            time = RouteTime.ArriveBy(Instant.parse("2026-07-19T10:00:00Z")),
            // WALK is a street mode, not a transit filter — it must drop out, leaving BUS + TRAM.
            allowedTransitModes = setOf(TransitMode.BUS, TransitMode.TRAM, TransitMode.WALK),
            // 2 transfers ⇒ wire maximumTransfers = 3 (the router counts boardings = transfers + 1).
            maxTransfers = 2,
            wheelchairAccessible = true,
            searchWindow = 30.minutes,
            reliability = Reliability.VERY_SAFE,
        ).toPlanTripRequest(before = null, after = null)

        val expected = json.parseToJsonElement(
            """
            {
              "dateTime": { "latestArrival": "2026-07-19T10:00:00Z" },
              "origin": { "location": { "stopLocation": { "stopLocationId": "1:U123" } } },
              "destination": { "location": { "coordinate": { "latitude": 49.2, "longitude": 16.6 } } },
              "modes": { "transit": { "transit": [ { "mode": "BUS" }, { "mode": "TRAM" } ] } },
              "preferences": {
                "accessibility": { "wheelchair": { "enabled": true } },
                "transit": { "transfer": { "maximumTransfers": 3 } }
              },
              "searchWindow": "PT30M",
              "reliability": "VERY_SAFE"
            }
            """.trimIndent(),
        )
        assertEquals(expected, encode(body))
    }

    @Test
    fun `a stop visit maps to stopLocationIds with its wait and omits a zero wait`() {
        val body = request.copy(
            via = listOf(
                ViaLocation.Visit(Location.Stop("1:V1"), minimumWaitTime = 5.minutes),
                ViaLocation.Visit(Location.Stop("1:V2")),
            ),
        ).toPlanTripRequest(before = null, after = null)

        val expected = json.parseToJsonElement(
            """
            [
              { "visit": { "stopLocationIds": ["1:V1"], "minimumWaitTime": "PT5M" } },
              { "visit": { "stopLocationIds": ["1:V2"] } }
            ]
            """.trimIndent(),
        )
        assertEquals(expected, encode(body)["via"])
    }

    @Test
    fun `a coordinate visit is rejected as an invalid via before any body is built`() {
        val coordinateVisit = request.copy(via = listOf(ViaLocation.Visit(Location.Coordinate(49.21, 16.59))))

        val plan = assertFailsWith<SpiderTransportException.BadRequest> {
            coordinateVisit.toPlanTripRequest(before = null, after = null)
        }
        val stream = assertFailsWith<SpiderTransportException.BadRequest> {
            coordinateVisit.toPlanStreamRequest(targetResults = 5, maxWindow = 120.minutes, before = null, after = null)
        }

        for (error in listOf(plan, stream)) {
            assertEquals("via", error.field)
            assertEquals("via is invalid", error.message)
        }
    }

    @Test
    fun `paging adds exactly one cursor to the original body`() {
        val first = encode(request.toPlanTripRequest(before = null, after = null))
        val next = encode(request.toPlanTripRequest(before = null, after = "c-next"))
        val previous = encode(request.toPlanTripRequest(before = "c-prev", after = null))

        assertEquals(first.keys + "after", next.keys)
        assertEquals(first.keys + "before", previous.keys)
        assertEquals(json.parseToJsonElement("\"c-next\""), next["after"])
        assertEquals(json.parseToJsonElement("\"c-prev\""), previous["before"])
        assertEquals(first, JsonObject(next.filterKeys { it != "after" }))
        assertEquals(first, JsonObject(previous.filterKeys { it != "before" }))
    }

    // numberOfDepartures and timeRange are required on the wire; startTime stays optional (absent = now).
    @Test
    fun `departures body always carries numberOfDepartures and timeRange`() {
        assertEquals(
            json.parseToJsonElement("""{"id":"1:S","numberOfDepartures":30,"timeRange":86400}"""),
            json.encodeToJsonElement(
                DeparturesRequest.serializer(),
                DeparturesRequest(id = "1:S", numberOfDepartures = 30, timeRange = 86_400),
            ),
        )
        assertEquals(
            json.parseToJsonElement("""{"id":"1:S","numberOfDepartures":10,"timeRange":3600,"startTime":1791612000}"""),
            json.encodeToJsonElement(
                DeparturesRequest.serializer(),
                DeparturesRequest(id = "1:S", numberOfDepartures = 10, timeRange = 3_600, startTime = 1_791_612_000),
            ),
        )
    }

    @Test
    fun `trip body carries the service date only when set`() {
        assertEquals(
            json.parseToJsonElement("""{"id":"1:T","serviceDate":"2026-10-07"}"""),
            json.encodeToJsonElement(TripRequest.serializer(), TripRequest(id = "1:T", serviceDate = "2026-10-07")),
        )
        assertEquals(
            json.parseToJsonElement("""{"id":"1:T"}"""),
            json.encodeToJsonElement(TripRequest.serializer(), TripRequest(id = "1:T")),
        )
    }

    @Test
    fun `PlanRequest with no filters omits modes and preferences`() {
        assertEquals(null, request.toModesInput(), "no allowedTransitModes ⇒ modes omitted")
        assertEquals(null, request.toPreferencesInput(), "no maxTransfers/wheelchair ⇒ preferences omitted")
    }

    @Test
    fun `plan response parses itineraries pageInfo and enums`() {
        val body =
            """
            {
              "itineraries":[{
                "start":"t1","end":"t2","numberOfTransfers":1,"duration":600,"waitingTime":0,
                "legs":[{
                  "mode":"BUS",
                  "start":{"scheduledTime":"t1"},"end":{"scheduledTime":"t2"},
                  "realtimeState":"SCHEDULED","realTime":false,"distance":1200.5,"duration":600,
                  "interlineWithPreviousLeg":false,"legGeometry":{"points":""},
                  "from":{"name":"A","stop":{"gtfsId":"1:U1","wheelchairBoarding":"POSSIBLE"}},
                  "to":{"name":"B"},
                  "route":{"gtfsId":"1:L12","shortName":"12","longName":"Line 12"},
                  "trip":{"gtfsId":"1:T1","bikesAllowed":"ALLOWED"}
                }]
              }],
              "pageInfo":{"hasNextPage":true,"hasPreviousPage":false,"startCursor":"a","endCursor":"b","searchWindowUsed":"PT1H"},
              "routingErrors":[],
              "searchDateTime":"2026-10-07T08:00:00+02:00"
            }
            """.trimIndent()

        val plan = json.decodeFromString(PlanTripResponse.serializer(), body)
        val leg = plan.itineraries.single().legs.single()
        assertEquals(Mode.BUS, leg.mode)
        assertEquals("1:U1", leg.from.stop!!.gtfsId)
        assertEquals(WheelchairBoarding.POSSIBLE, leg.from.stop.wheelchairBoarding)
        assertEquals(BikesAllowed.ALLOWED, leg.trip!!.bikesAllowed)
        assertEquals("b", plan.pageInfo.endCursor)
        assertEquals("2026-10-07T08:00:00+02:00", plan.searchDateTime)
    }

    @Test
    fun `unknown enum value maps to the typed UNKNOWN case instead of throwing`() {
        val body =
            """
            {
              "itineraries":[{
                "start":"t1","end":"t2","numberOfTransfers":0,"duration":1,"waitingTime":0,
                "legs":[{
                  "mode":"HELICOPTER",
                  "start":{"scheduledTime":"t1"},"end":{"scheduledTime":"t2"},
                  "realtimeState":"SCHEDULED","realTime":false,"distance":10.0,"duration":1,
                  "interlineWithPreviousLeg":false,"legGeometry":{"points":""},
                  "from":{"name":"A"},"to":{"name":"B"}
                }]
              }],
              "pageInfo":{"hasNextPage":false,"hasPreviousPage":false},
              "routingErrors":[],
              "searchDateTime":"2026-10-07T08:00:00+02:00"
            }
            """.trimIndent()

        val plan = json.decodeFromString(PlanTripResponse.serializer(), body)
        assertEquals(Mode.UNKNOWN, plan.itineraries.single().legs.single().mode)
    }

    @Test
    fun `departures and trip responses read one root that is null for an unknown id`() {
        val board = json.decodeFromString(
            DeparturesResponse.serializer(),
            """{"stop":{"gtfsId":"1:S","name":"Hlavní nádraží","wheelchairBoarding":null,"stoptimesWithoutPatterns":[]}}""",
        ).stop!!
        assertEquals("1:S", board.gtfsId)
        assertNull(board.wheelchairBoarding)
        assertNull(json.decodeFromString(DeparturesResponse.serializer(), """{"stop":null}""").stop)

        val trip = json.decodeFromString(
            TripResponse.serializer(),
            """{"trip":{"gtfsId":"1:T","bikesAllowed":"NO_INFORMATION","wheelchairAccessible":"NO_INFORMATION",
              |"route":{"gtfsId":"1:L1","mode":"TRAM"},"stoptimesForDate":[]}}""".trimMargin(),
        ).trip!!
        assertEquals("1:L1", trip.route.gtfsId)
        assertNull(json.decodeFromString(TripResponse.serializer(), """{"trip":null}""").trip)
    }
}
