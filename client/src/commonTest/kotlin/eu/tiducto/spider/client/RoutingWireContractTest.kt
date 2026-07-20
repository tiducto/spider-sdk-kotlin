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
                  "from":{"name":"A","stop":{"wheelchairBoarding":"POSSIBLE"}},
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
}
