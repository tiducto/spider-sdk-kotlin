package eu.tiducto.spider.client

import eu.tiducto.spider.contract.routing.BikesAllowed
import eu.tiducto.spider.contract.routing.DeparturesRequest
import eu.tiducto.spider.contract.routing.DeparturesResponse
import eu.tiducto.spider.contract.routing.Mode
import eu.tiducto.spider.contract.routing.PickupDropoffType
import eu.tiducto.spider.contract.routing.PlanStreamRequest
import eu.tiducto.spider.contract.routing.PlanTripRequest
import eu.tiducto.spider.contract.routing.PlanTripResponse
import eu.tiducto.spider.contract.routing.Reliability as WireReliability
import eu.tiducto.spider.contract.routing.TransitMode as WireTransitMode
import eu.tiducto.spider.contract.routing.TripRequest
import eu.tiducto.spider.contract.routing.TripResponse
import eu.tiducto.spider.contract.routing.WheelchairBoarding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
    fun `excluded transit modes map to modes transit exclude without the street and unknown modes`() {
        val body = request.copy(
            excludedTransitModes = setOf(TransitMode.TRAM, TransitMode.WALK, TransitMode.UNKNOWN, TransitMode.RAIL),
        )

        val expected = json.parseToJsonElement("""{ "transit": { "exclude": ["TRAM", "RAIL"] } }""")
        assertEquals(expected, encode(body.toPlanTripRequest(before = null, after = null))["modes"])
        assertEquals(
            expected,
            json.encodeToJsonElement(
                PlanStreamRequest.serializer(),
                body.toPlanStreamRequest(targetResults = 5, maxWindow = 120.minutes, before = null, after = null),
            ).jsonObject["modes"],
        )
    }

    @Test
    fun `an empty exclusion is no filter`() {
        for (excluded in listOf(emptySet(), setOf(TransitMode.WALK, TransitMode.UNKNOWN))) {
            assertNull(request.copy(excludedTransitModes = excluded).toModesInput(), "$excluded")
            assertEquals(
                json.parseToJsonElement("""{ "transit": { "transit": [ { "mode": "BUS" } ] } }"""),
                encode(
                    request.copy(allowedTransitModes = setOf(TransitMode.BUS), excludedTransitModes = excluded)
                        .toPlanTripRequest(before = null, after = null),
                )["modes"],
                "$excluded beside an allow-list",
            )
        }
        assertEquals(
            json.parseToJsonElement("""{ "transit": { "exclude": ["TRAM"] } }"""),
            encode(
                request.copy(allowedTransitModes = setOf(TransitMode.WALK), excludedTransitModes = setOf(TransitMode.TRAM))
                    .toPlanTripRequest(before = null, after = null),
            )["modes"],
            "an allow-list of street modes only is no filter",
        )
    }

    @Test
    fun `an allow-list with an exclusion is rejected as an invalid modes transit before any body is built`() {
        val both = request.copy(allowedTransitModes = setOf(TransitMode.BUS), excludedTransitModes = setOf(TransitMode.TRAM))

        val plan = assertFailsWith<SpiderTransportException.BadRequest> { both.toPlanTripRequest(before = null, after = null) }
        val stream = assertFailsWith<SpiderTransportException.BadRequest> {
            both.toPlanStreamRequest(targetResults = 5, maxWindow = 120.minutes, before = null, after = null)
        }

        for (error in listOf(plan, stream)) {
            assertEquals("modes.transit", error.field)
            assertEquals("modes.transit is invalid", error.message)
        }
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
        assertEquals(
            json.parseToJsonElement("""{"id":"1:S","numberOfDepartures":10,"timeRange":3600,"excludeModes":["TRAM","BUS"]}"""),
            json.encodeToJsonElement(
                DeparturesRequest.serializer(),
                DeparturesRequest(
                    id = "1:S",
                    numberOfDepartures = 10,
                    timeRange = 3_600,
                    excludeModes = listOf(WireTransitMode.TRAM, WireTransitMode.BUS),
                ),
            ),
        )
    }

    @Test
    fun `trip body carries the service date and the reliability only when set`() {
        assertEquals(
            json.parseToJsonElement("""{"id":"1:T","serviceDate":"2026-10-07","reliability":"SAFE"}"""),
            json.encodeToJsonElement(
                TripRequest.serializer(),
                TripRequest(id = "1:T", serviceDate = "2026-10-07", reliability = WireReliability.SAFE),
            ),
        )
        assertEquals(
            json.parseToJsonElement("""{"id":"1:T","serviceDate":"2026-10-07"}"""),
            json.encodeToJsonElement(TripRequest.serializer(), TripRequest(id = "1:T", serviceDate = "2026-10-07")),
        )
    }

    @Test
    fun `trip body carries the run's start time only when set`() {
        assertEquals(
            json.parseToJsonElement("""{"id":"1:T","serviceDate":"2026-10-07","startTime":"25:10:00"}"""),
            json.encodeToJsonElement(
                TripRequest.serializer(),
                TripRequest(id = "1:T", serviceDate = "2026-10-07", startTime = "25:10:00"),
            ),
        )
    }

    @Test
    fun `no preferences and empty preferences send exactly the body without them`() {
        val bare = request.copy(maxTransfers = 1, wheelchairAccessible = true)
        val expected = encode(bare.toPlanTripRequest(before = null, after = null))

        for (preferences in listOf(null, RoutePreferences(), RoutePreferences(excludedRoutes = emptySet(), excludedAgencies = emptySet()))) {
            assertEquals(expected, encode(bare.copy(preferences = preferences).toPlanTripRequest(before = null, after = null)), "$preferences")
        }
        assertNull(request.copy(preferences = RoutePreferences()).toPreferencesInput())
    }

    @Test
    fun `every preference lands at its contract path beside maximumTransfers and wheelchair`() {
        val preferred = request.copy(
            maxTransfers = 2,
            wheelchairAccessible = true,
            preferences = RoutePreferences(
                walkSpeed = 1.2,
                walkReluctance = 4.5,
                boardCost = 600,
                transferCost = 300,
                transferSlack = 2.minutes,
                waitReluctance = 0.5,
                boardSlack = 30.seconds,
                alightSlack = 1.hours,
                excludedRoutes = setOf("1:L4", "1:L9"),
                excludedAgencies = setOf("1:DPMB"),
            ),
        )

        val expected = json.parseToJsonElement(
            """
            {
              "street": { "walk": { "speed": 1.2, "reluctance": 4.5, "boardCost": 600 } },
              "transit": {
                "transfer": { "cost": 300, "slack": "PT2M", "maximumTransfers": 3 },
                "board": { "waitReluctance": 0.5, "slack": "PT30S" },
                "alight": { "slack": "PT1H" },
                "filters": [ { "exclude": [ { "routes": ["1:L4", "1:L9"] }, { "agencies": ["1:DPMB"] } ] } ]
              },
              "accessibility": { "wheelchair": { "enabled": true } }
            }
            """.trimIndent(),
        )
        assertEquals(expected, encode(preferred.toPlanTripRequest(before = null, after = null))["preferences"])
        assertEquals(
            expected,
            json.encodeToJsonElement(
                PlanStreamRequest.serializer(),
                preferred.toPlanStreamRequest(targetResults = 5, maxWindow = 120.minutes, before = null, after = null),
            ).jsonObject["preferences"],
        )
    }

    @Test
    fun `a single preference sends only its own branch`() {
        fun preferencesOf(preferences: RoutePreferences) =
            encode(request.copy(preferences = preferences).toPlanTripRequest(before = null, after = null))["preferences"]

        assertEquals(
            json.parseToJsonElement("""{ "transit": { "transfer": { "slack": "PT0S" } } }"""),
            preferencesOf(RoutePreferences(transferSlack = Duration.ZERO)),
        )
        assertEquals(
            json.parseToJsonElement("""{ "street": { "walk": { "speed": 0.1 } } }"""),
            preferencesOf(RoutePreferences(walkSpeed = 0.1)),
        )
        assertEquals(
            json.parseToJsonElement("""{ "transit": { "filters": [ { "exclude": [ { "agencies": ["1:A"] } ] } ] } }"""),
            preferencesOf(RoutePreferences(excludedRoutes = emptySet(), excludedAgencies = setOf("1:A"))),
        )
    }

    @Test
    fun `a preference out of its contract range is rejected with its path before any body is built`() {
        val outOfRange = listOf(
            RoutePreferences(walkSpeed = 0.09) to "preferences.street.walk.speed",
            RoutePreferences(walkSpeed = Double.POSITIVE_INFINITY) to "preferences.street.walk.speed",
            RoutePreferences(walkReluctance = 100_001.0) to "preferences.street.walk.reluctance",
            RoutePreferences(boardCost = -1) to "preferences.street.walk.boardCost",
            RoutePreferences(transferCost = 1_000_001) to "preferences.transit.transfer.cost",
            RoutePreferences(transferSlack = (-1).seconds) to "preferences.transit.transfer.slack",
            RoutePreferences(waitReluctance = 0.0) to "preferences.transit.board.waitReluctance",
            RoutePreferences(boardSlack = 61.minutes) to "preferences.transit.board.slack",
            RoutePreferences(alightSlack = 1.hours + 1.seconds) to "preferences.transit.alight.slack",
        )

        for ((preferences, field) in outOfRange) {
            val planned = request.copy(preferences = preferences)
            val plan = assertFailsWith<SpiderTransportException.BadRequest> { planned.toPlanTripRequest(before = null, after = null) }
            val stream = assertFailsWith<SpiderTransportException.BadRequest> {
                planned.toPlanStreamRequest(targetResults = 5, maxWindow = 120.minutes, before = null, after = null)
            }
            for (error in listOf(plan, stream)) {
                assertEquals(field, error.field, "$preferences")
                assertEquals("$field is out of range", error.message)
            }
        }
    }

    @Test
    fun `transitOnly sends modes transitOnly beside the mode filters and nothing when false`() {
        fun modesOf(planned: PlanRequest) = listOf(
            encode(planned.toPlanTripRequest(before = null, after = null))["modes"],
            json.encodeToJsonElement(
                PlanStreamRequest.serializer(),
                planned.toPlanStreamRequest(targetResults = 5, maxWindow = 120.minutes, before = null, after = null),
            ).jsonObject["modes"],
        )

        val only = json.parseToJsonElement("""{ "transitOnly": true }""")
        assertEquals(listOf(only, only), modesOf(request.copy(transitOnly = true)))
        val withExclusion = json.parseToJsonElement("""{ "transitOnly": true, "transit": { "exclude": ["TRAM"] } }""")
        assertEquals(
            listOf(withExclusion, withExclusion),
            modesOf(request.copy(transitOnly = true, excludedTransitModes = setOf(TransitMode.TRAM))),
        )
        assertEquals(listOf(null, null), modesOf(request.copy(transitOnly = false)))
        assertEquals(
            encode(request.toPlanTripRequest(before = null, after = null)),
            encode(request.copy(transitOnly = false).toPlanTripRequest(before = null, after = null)),
        )
    }

    @Test
    fun `numberOfItineraries is first on the first page and after a cursor and last before one`() {
        val sized = request.copy(numberOfItineraries = 3)
        fun countsOf(body: PlanTripRequest) = encode(body).let { it["first"] to it["last"] }
        val three = json.parseToJsonElement("3")

        assertEquals(three to null, countsOf(sized.toPlanTripRequest(before = null, after = null)))
        assertEquals(three to null, countsOf(sized.toPlanTripRequest(before = null, after = "c-next")))
        assertEquals(null to three, countsOf(sized.toPlanTripRequest(before = "c-prev", after = null)))
        for ((before, after) in listOf(null to null, null to "c-next", "c-prev" to null)) {
            assertEquals(null to null, countsOf(request.toPlanTripRequest(before = before, after = after)))
        }
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
                  "pickupType":"CALL_AGENCY","dropoffType":"SCHEDULED","interlineWithPreviousLeg":false,"legGeometry":{"points":""},
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
        assertEquals(PickupDropoffType.CALL_AGENCY, leg.pickupType)
        assertEquals(PickupDropoffType.SCHEDULED, leg.dropoffType)
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
                  "pickupType":"SCHEDULED","dropoffType":"SOMETHING_NEW","interlineWithPreviousLeg":false,"legGeometry":{"points":""},
                  "from":{"name":"A"},"to":{"name":"B"}
                }]
              }],
              "pageInfo":{"hasNextPage":false,"hasPreviousPage":false},
              "routingErrors":[],
              "searchDateTime":"2026-10-07T08:00:00+02:00"
            }
            """.trimIndent()

        val plan = json.decodeFromString(PlanTripResponse.serializer(), body)
        val leg = plan.itineraries.single().legs.single()
        assertEquals(Mode.UNKNOWN, leg.mode)
        assertEquals(PickupDropoffType.UNKNOWN, leg.dropoffType)
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

    private fun modesOf(planned: PlanRequest) = listOf(
        encode(planned.toPlanTripRequest(before = null, after = null))["modes"],
        json.encodeToJsonElement(
            PlanStreamRequest.serializer(),
            planned.toPlanStreamRequest(targetResults = 5, maxWindow = 120.minutes, before = null, after = null),
        ).jsonObject["modes"],
    )

    @Test
    fun `mode reluctance is sent as costs beside exclude in TransitMode order`() {
        val planned = request.copy(
            excludedTransitModes = setOf(TransitMode.RAIL),
            preferences = RoutePreferences(modeReluctance = mapOf(TransitMode.TROLLEYBUS to 0.9, TransitMode.BUS to 1.5, TransitMode.RAIL to 2.5)),
        )

        val expected = json.parseToJsonElement(
            """{ "transit": { "exclude": ["RAIL"], "costs": [
              { "mode": "BUS", "reluctance": 1.5 }, { "mode": "TROLLEYBUS", "reluctance": 0.9 }, { "mode": "RAIL", "reluctance": 2.5 }
            ] } }""",
        )
        assertEquals(listOf(expected, expected), modesOf(planned))
    }

    @Test
    fun `mode reluctance alone is sent as costs without the street and unknown modes`() {
        val planned = request.copy(
            preferences = RoutePreferences(
                modeReluctance = mapOf(TransitMode.TRAM to 0.5, TransitMode.WALK to 3.0, TransitMode.UNKNOWN to 2.0),
            ),
        )

        val expected = json.parseToJsonElement("""{ "transit": { "costs": [ { "mode": "TRAM", "reluctance": 0.5 } ] } }""")
        assertEquals(listOf(expected, expected), modesOf(planned))
        assertNull(encode(planned.toPlanTripRequest(before = null, after = null))["preferences"])
    }

    @Test
    fun `mode reluctance folds into an allow-list and drops the modes it does not allow`() {
        val planned = request.copy(
            allowedTransitModes = setOf(TransitMode.BUS, TransitMode.TRAM),
            preferences = RoutePreferences(modeReluctance = mapOf(TransitMode.TRAM to 0.9, TransitMode.RAIL to 5.0)),
        )

        val expected = json.parseToJsonElement(
            """{ "transit": { "transit": [ { "mode": "BUS" }, { "mode": "TRAM", "cost": { "reluctance": 0.9 } } ] } }""",
        )
        assertEquals(listOf(expected, expected), modesOf(planned))
    }

    @Test
    fun `a null or empty mode reluctance sends no costs`() {
        for (reluctance in listOf(null, emptyMap(), mapOf(TransitMode.WALK to 2.0))) {
            val planned = request.copy(preferences = RoutePreferences(modeReluctance = reluctance))
            assertEquals(listOf(null, null), modesOf(planned), "$reluctance")
            assertEquals(
                encode(request.toPlanTripRequest(before = null, after = null)),
                encode(planned.toPlanTripRequest(before = null, after = null)),
            )
            val excluding = json.parseToJsonElement("""{ "transit": { "exclude": ["TRAM"] } }""")
            assertEquals(
                listOf(excluding, excluding),
                modesOf(planned.copy(excludedTransitModes = setOf(TransitMode.TRAM))),
                "$reluctance beside an exclusion",
            )
        }
    }

    @Test
    fun `a mode reluctance out of range is rejected with its path before any body is built`() {
        val outOfRange = listOf(0.09, 100_001.0, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)
        val allowed = setOf(TransitMode.BUS)
        for (value in outOfRange) {
            for ((planned, field) in listOf(
                request.copy(preferences = RoutePreferences(modeReluctance = mapOf(TransitMode.TRAM to value))) to
                    "modes.transit.costs.reluctance",
                request.copy(
                    allowedTransitModes = allowed,
                    preferences = RoutePreferences(modeReluctance = mapOf(TransitMode.BUS to value)),
                ) to "modes.transit.transit.cost.reluctance",
            )) {
                val plan = assertFailsWith<SpiderTransportException.BadRequest> { planned.toPlanTripRequest(before = null, after = null) }
                val stream = assertFailsWith<SpiderTransportException.BadRequest> {
                    planned.toPlanStreamRequest(targetResults = 5, maxWindow = 120.minutes, before = null, after = null)
                }
                for (error in listOf(plan, stream)) {
                    assertEquals(field, error.field, "$value")
                    assertEquals("$field is out of range", error.message)
                }
            }
        }
        for (value in listOf(0.1, 100_000.0)) {
            request.copy(preferences = RoutePreferences(modeReluctance = mapOf(TransitMode.TRAM to value)))
                .toPlanTripRequest(before = null, after = null)
        }
    }

    @Test
    fun `stops and departures decode their parent station and null without one`() {
        val board = json.decodeFromString(
            DeparturesResponse.serializer(),
            """{"stop":{"gtfsId":"1:S1","name":"Česká","wheelchairBoarding":"POSSIBLE",
              |"parentStation":{"gtfsId":"1:S","name":"Česká"},"stoptimesWithoutPatterns":[]}}""".trimMargin(),
        ).stop!!
        assertEquals("1:S" to "Česká", board.parentStation!!.let { it.gtfsId to it.name })
        val station = json.decodeFromString(
            DeparturesResponse.serializer(),
            """{"stop":{"gtfsId":"1:S","name":"Česká","wheelchairBoarding":null,"parentStation":null,"stoptimesWithoutPatterns":[]}}""",
        ).stop!!
        assertNull(station.parentStation)
    }
}
