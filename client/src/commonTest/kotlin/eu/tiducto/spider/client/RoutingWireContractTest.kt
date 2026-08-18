package eu.tiducto.spider.client

import eu.tiducto.spider.contract.routing.BikesAllowed
import eu.tiducto.spider.contract.routing.Mode
import eu.tiducto.spider.contract.routing.PlanConnectionResponse
import eu.tiducto.spider.contract.routing.PlanConnectionVariables
import eu.tiducto.spider.contract.routing.PlanCoordinateInput
import eu.tiducto.spider.contract.routing.PlanDateTimeInput
import eu.tiducto.spider.contract.routing.PlanLabeledLocationInput
import eu.tiducto.spider.contract.routing.PlanLocationInput
import eu.tiducto.spider.contract.routing.PlanPassThroughViaLocationInput
import eu.tiducto.spider.contract.routing.PlanStopLocationInput
import eu.tiducto.spider.contract.routing.PlanViaLocationInput
import eu.tiducto.spider.contract.routing.WheelchairBoarding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/**
 * Guards the routing wire format the client speaks to the persisted-query gateway. The client now builds
 * requests from — and parses responses into — the generated :contract models; this test pins the
 * serialized shape so a future contract regeneration can't silently change the bytes on the wire
 * (a mismatch there is a 403 at the gateway or a mis-parsed response, both live-affecting).
 *
 * The Json config mirrors RoutingClient's exactly — the two must stay in lockstep.
 */
class RoutingWireContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `plan variables serialize to the expected wire shape without nulls or label`() {
        val variables = PlanConnectionVariables(
            dateTime = PlanDateTimeInput(earliestDeparture = "2026-07-19T10:00:00Z"),
            origin = PlanLabeledLocationInput(
                location = PlanLocationInput(stopLocation = PlanStopLocationInput(stopLocationId = "1:U123")),
            ),
            destination = PlanLabeledLocationInput(
                location = PlanLocationInput(coordinate = PlanCoordinateInput(latitude = 49.2, longitude = 16.6)),
            ),
            via = listOf(
                PlanViaLocationInput(passThrough = PlanPassThroughViaLocationInput(stopLocationIds = listOf("1:U999"))),
            ),
            first = 5,
        )

        // explicitNulls=false must drop: latestArrival, the optional `label` on every input,
        // the unused `coordinate`/`stopLocation` alternatives, and before/after.
        val expected = json.parseToJsonElement(
            """
            {
              "dateTime": { "earliestDeparture": "2026-07-19T10:00:00Z" },
              "origin": { "location": { "stopLocation": { "stopLocationId": "1:U123" } } },
              "destination": { "location": { "coordinate": { "latitude": 49.2, "longitude": 16.6 } } },
              "via": [ { "passThrough": { "stopLocationIds": ["1:U999"] } } ],
              "first": 5
            }
            """.trimIndent(),
        )

        val actual = json.encodeToJsonElement(PlanConnectionVariables.serializer(), variables)
        assertEquals(expected, actual)
    }

    @Test
    fun `PlanRequest maps modes transfers wheelchair and search window to OTP inputs`() {
        val request = PlanRequest(
            origin = Location.Stop("1:U123"),
            destination = Location.Coordinate(49.2, 16.6),
            time = RouteTime.DepartAt(Instant.parse("2026-07-19T10:00:00Z")),
            // WALK is a street mode, not a transit filter — it must drop out, leaving BUS + TRAM.
            allowedTransitModes = setOf(TransitMode.BUS, TransitMode.TRAM, TransitMode.WALK),
            maxTransfers = 2,
            wheelchairAccessible = true,
            searchWindow = 30.minutes,
        )
        val variables = PlanConnectionVariables(
            dateTime = PlanDateTimeInput(earliestDeparture = "2026-07-19T10:00:00Z"),
            origin = PlanLabeledLocationInput(
                location = PlanLocationInput(stopLocation = PlanStopLocationInput(stopLocationId = "1:U123")),
            ),
            destination = PlanLabeledLocationInput(
                location = PlanLocationInput(coordinate = PlanCoordinateInput(latitude = 49.2, longitude = 16.6)),
            ),
            modes = request.toModesInput(),
            preferences = request.toPreferencesInput(),
            searchWindow = request.searchWindow.toSearchWindowIso(),
        )

        val expected = json.parseToJsonElement(
            """
            {
              "dateTime": { "earliestDeparture": "2026-07-19T10:00:00Z" },
              "origin": { "location": { "stopLocation": { "stopLocationId": "1:U123" } } },
              "destination": { "location": { "coordinate": { "latitude": 49.2, "longitude": 16.6 } } },
              "modes": { "transit": { "transit": [ { "mode": "BUS" }, { "mode": "TRAM" } ] } },
              "preferences": {
                "accessibility": { "wheelchair": { "enabled": true } },
                "transit": { "transfer": { "maximumTransfers": 2 } }
              },
              "searchWindow": "PT30M"
            }
            """.trimIndent(),
        )

        val actual = json.encodeToJsonElement(PlanConnectionVariables.serializer(), variables)
        assertEquals(expected, actual)
    }

    @Test
    fun `search window floors to whole minutes with a one-minute floor`() {
        // A sub-minute window would search almost nothing on OTP; floor it to a usable PT1M instead.
        assertEquals("PT1M", Duration.ZERO.toSearchWindowIso())
        assertEquals("PT1M", 5.seconds.toSearchWindowIso())
        assertEquals("PT1M", 90.seconds.toSearchWindowIso())
        assertEquals("PT30M", 30.minutes.toSearchWindowIso())
        assertEquals("PT60M", 1.hours.toSearchWindowIso())
    }

    @Test
    fun `PlanRequest with no filters omits modes and preferences`() {
        val request = PlanRequest(
            origin = Location.Stop("1:U1"),
            destination = Location.Stop("1:U2"),
            time = RouteTime.DepartAt(Instant.parse("2026-07-19T10:00:00Z")),
        )
        assertEquals(null, request.toModesInput(), "no allowedTransitModes ⇒ modes omitted")
        assertEquals(null, request.toPreferencesInput(), "no maxTransfers/wheelchair ⇒ preferences omitted")
    }

    @Test
    fun `plan response parses payload models and enums`() {
        val body =
            """
            {"data":{"planConnection":{
              "pageInfo":{"hasNextPage":true,"hasPreviousPage":false,"startCursor":"a","endCursor":"b"},
              "routingErrors":[],
              "edges":[{"cursor":"c","node":{
                "numberOfTransfers":1,"duration":600,
                "legs":[{
                  "mode":"BUS",
                  "start":{"scheduledTime":"t1"},"end":{"scheduledTime":"t2"},
                  "from":{"name":"A","stop":{"gtfsId":"1:U1","wheelchairBoarding":"POSSIBLE"}},
                  "to":{"name":"B"},
                  "route":{"shortName":"12","longName":"Line 12"},
                  "trip":{"gtfsId":"1:T1","bikesAllowed":"ALLOWED"}
                }]
              }}]
            }}}
            """.trimIndent()

        val env = json.decodeFromString(PlanConnectionResponse.serializer(), body)
        val leg = env.data!!.planConnection!!.edges!!.single().node.legs.single()
        assertEquals(Mode.BUS, leg.mode)
        assertEquals("1:U1", leg.from.stop!!.gtfsId)
        assertEquals(WheelchairBoarding.POSSIBLE, leg.from.stop!!.wheelchairBoarding)
        assertEquals(BikesAllowed.ALLOWED, leg.trip!!.bikesAllowed)
    }

    @Test
    fun `unknown enum value maps to the typed UNKNOWN case instead of throwing`() {
        // A mode the pinned contract enum doesn't know (the upstream engine adds one later). The generated enum's
        // custom serializer must decode it to UNKNOWN — the whole response still parses.
        val body =
            """
            {"data":{"planConnection":{
              "pageInfo":{"hasNextPage":false,"hasPreviousPage":false},
              "routingErrors":[],
              "edges":[{"cursor":"c","node":{
                "numberOfTransfers":0,"duration":1,
                "legs":[{
                  "mode":"HELICOPTER",
                  "start":{"scheduledTime":"t1"},"end":{"scheduledTime":"t2"},
                  "from":{"name":"A"},"to":{"name":"B"}
                }]
              }}]
            }}}
            """.trimIndent()

        val env = json.decodeFromString(PlanConnectionResponse.serializer(), body)
        assertEquals(Mode.UNKNOWN, env.data!!.planConnection!!.edges!!.single().node.legs.single().mode)
    }

    @Test
    fun `persisted query ids are well-formed sha-256 and route suffixes match the contract`() {
        // The id is the SHA-256 of the canonical query text, so a breaking contract change rotates it — pin
        // its shape and the stable route suffixes, not the exact hash (the contract owns the value).
        val sha256Hex = Regex("^[0-9a-f]{64}$")
        for (op in listOf(PersistedQueries.PLAN, PersistedQueries.DEPARTURES, PersistedQueries.TRIP)) {
            assertTrue(sha256Hex.matches(op.id), "persisted-query id must be lowercase hex SHA-256: ${op.id}")
        }
        assertEquals("plan", PersistedQueries.PLAN.path)
        assertEquals("departures", PersistedQueries.DEPARTURES.path)
        assertEquals("trip", PersistedQueries.TRIP.path)
    }
}
