package eu.tiducto.spider.client

import eu.tiducto.spider.contract.routing.PlanStreamRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The Json config mirrors RoutingClient's exactly; the two must stay in lockstep. */
class RoutingStreamTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // A `chunk` carries itinerary nodes; realtime delays ride on each leg's estimated{time,delay} + realtimeState
    // + realTime and must land on the domain Leg exactly as the batch plan maps them.
    @Test
    fun `chunk maps itineraries with realtime delays`() {
        val data = """
            {
              "frontier": 1800, "found": 1, "finalized": 1,
              "results": [
                {
                  "numberOfTransfers": 1,
                  "start": "2026-07-15T08:00:00Z", "end": "2026-07-15T08:30:00Z", "duration": 1800,
                  "legs": [
                    {
                      "mode": "BUS",
                      "start": { "scheduledTime": "2026-07-15T08:00:00Z", "estimated": { "time": "2026-07-15T08:01:00Z", "delay": "PT60S" } },
                      "end":   { "scheduledTime": "2026-07-15T08:30:00Z", "estimated": { "time": "2026-07-15T08:32:00Z", "delay": "PT120S" } },
                      "realtimeState": "UPDATED", "realTime": true, "serviceDate": "2026-07-15",
                      "typicalArrivalDelay": 90, "interlineWithPreviousLeg": true,
                      "from": { "name": "Origin", "stop": { "gtfsId": "1:A", "platformCode": "3", "zoneId": "100",
                                                            "wheelchairBoarding": "NO_INFORMATION" } },
                      "to":   { "name": "Dest",   "stop": { "gtfsId": "1:B", "platformCode": "B", "zoneId": "101",
                                                            "wheelchairBoarding": "SOMETHING_NEW" } },
                      "route": { "gtfsId": "1:L12", "shortName": "12", "color": "0055A4", "textColor": "FFFFFF" },
                      "trip": { "gtfsId": "1:T", "bikesAllowed": "NOT_ALLOWED" }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val event = assertIs<PlanStreamEvent.Result>(parsePlanStreamRecord("chunk", data, json))

        val itinerary = event.itineraries.single()
        assertEquals(1, itinerary.numberOfTransfers)
        assertEquals(1800L, itinerary.durationSeconds)

        val leg = itinerary.legs.single()
        assertEquals(TransitMode.BUS, leg.mode)
        assertEquals(60.seconds, leg.startDelay)
        assertEquals(120.seconds, leg.endDelay)
        assertEquals("2026-07-15T08:01:00Z", leg.startEstimated)
        assertEquals(true, leg.isRealtime)
        assertEquals(RealtimeState.UPDATED, leg.realtimeState)
        assertEquals("2026-07-15", leg.serviceDate)
        assertEquals(90.seconds, leg.typicalArrivalDelay)
        assertEquals(true, leg.interlineWithPreviousLeg)
        assertEquals("1:A", leg.fromGtfsId)
        assertEquals("1:B", leg.toGtfsId)
        assertEquals("1:L12", leg.routeGtfsId)
        assertEquals("0055A4", leg.routeColor)
        assertEquals("FFFFFF", leg.routeTextColor)
        assertEquals("3", leg.fromPlatformCode)
        assertEquals("B", leg.toPlatformCode)
        assertEquals("100", leg.fromZoneId)
        assertEquals("101", leg.toZoneId)
        // NO_INFORMATION is no information; a value the SDK doesn't know is UNKNOWN.
        assertEquals(null, leg.fromWheelchair)
        assertEquals(WheelchairBoarding.UNKNOWN, leg.toWheelchair)
        assertEquals(BikesAllowed.NOT_ALLOWED, leg.bikesAllowed)
    }

    // The `pageInfo` frame is terminal: it maps to Done carrying the continuation cursors the caller feeds
    // to planStreamNext / planStreamPrevious.
    @Test
    fun `pageInfo maps to the terminal Done with continuation cursors`() {
        val data = """{ "startCursor": "c-prev", "endCursor": "c-next", "hasNextPage": true, "hasPreviousPage": false, "searchWindowUsed": "PT1H", "routingErrors": [] }"""
        val event = assertIs<PlanStreamEvent.Done>(parsePlanStreamRecord("pageInfo", data, json))
        assertEquals("c-prev", event.pageInfo.startCursor)
        assertEquals("c-next", event.pageInfo.endCursor)
        assertEquals(true, event.pageInfo.hasNextPage)
        assertEquals(false, event.pageInfo.hasPreviousPage)
        assertEquals("PT1H", event.pageInfo.searchWindowUsed)
        assertEquals(emptyList(), event.routingErrors)
    }

    // Routing errors ride on the final pageInfo, shaped as in batch planConnection, so a search outside the
    // feed's dates is an outcome in the stream too, not a failure.
    @Test
    fun `pageInfo routingErrors map to Done with typed codes`() {
        val data = """
            {
              "hasNextPage": false, "hasPreviousPage": false,
              "routingErrors": [
                { "code": "OUTSIDE_SERVICE_PERIOD", "description": "date is outside the feed", "inputField": "DATE_TIME" },
                { "code": "LOCATION_NOT_FOUND", "description": "unknown stop", "inputField": "FROM" },
                { "code": "SOMETHING_NEW", "description": "added later", "inputField": "SOMEWHERE_NEW" }
              ]
            }
        """.trimIndent()
        val event = assertIs<PlanStreamEvent.Done>(parsePlanStreamRecord("pageInfo", data, json))
        assertEquals(
            listOf(
                RoutingError(RoutingErrorCode.OUTSIDE_SERVICE_PERIOD, "date is outside the feed", InputField.DATE_TIME),
                RoutingError(RoutingErrorCode.LOCATION_NOT_FOUND, "unknown stop", InputField.FROM),
                RoutingError(RoutingErrorCode.UNKNOWN, "added later", InputField.UNKNOWN),
            ),
            event.routingErrors,
        )
    }

    @Test
    fun `unknown realtime state and new modes map to typed values`() {
        val data = """
            {
              "frontier": 300, "found": 1, "finalized": 1,
              "results": [
                {
                  "numberOfTransfers": 0, "duration": 300,
                  "legs": [
                    {
                      "mode": "FUNICULAR", "realtimeState": "SOMETHING_NEW",
                      "start": { "scheduledTime": "2026-07-15T08:00:00Z" }, "end": { "scheduledTime": "2026-07-15T08:05:00Z" },
                      "from": { "name": "Újezd" }, "to": { "name": "Petřín" }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val leg = assertIs<PlanStreamEvent.Result>(parsePlanStreamRecord("chunk", data, json)).itineraries.single().legs.single()
        assertEquals(TransitMode.FUNICULAR, leg.mode)
        assertEquals(RealtimeState.UNKNOWN, leg.realtimeState)
        assertEquals(
            listOf(null, null, null, null, null, null, null),
            listOf(leg.routeGtfsId, leg.routeColor, leg.routeTextColor, leg.fromPlatformCode, leg.toPlatformCode, leg.fromZoneId, leg.toZoneId),
        )
    }

    // Without a reliability (or history) the router sends a null typicalArrivalDelay; interline is false unless set.
    @Test
    fun `null or absent typical arrival delay and interline flag map to null and false`() {
        val data = """
            {
              "frontier": 600, "found": 1, "finalized": 1,
              "results": [
                {
                  "numberOfTransfers": 0, "duration": 600,
                  "legs": [
                    {
                      "mode": "TRAM", "typicalArrivalDelay": null, "interlineWithPreviousLeg": null,
                      "start": { "scheduledTime": "2026-07-15T08:00:00Z" }, "end": { "scheduledTime": "2026-07-15T08:05:00Z" },
                      "from": { "name": "A" }, "to": { "name": "B" }
                    },
                    {
                      "mode": "TRAM",
                      "start": { "scheduledTime": "2026-07-15T08:05:00Z" }, "end": { "scheduledTime": "2026-07-15T08:10:00Z" },
                      "from": { "name": "B" }, "to": { "name": "C" }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val legs = assertIs<PlanStreamEvent.Result>(parsePlanStreamRecord("chunk", data, json)).itineraries.single().legs
        assertEquals(listOf(null, null), legs.map { it.typicalArrivalDelay })
        assertEquals(listOf(false, false), legs.map { it.interlineWithPreviousLeg })
    }

    @Test
    fun `done telemetry frame is ignored`() {
        val data = """{ "iterations": 3, "windowSeconds": 3600, "resultCount": 5, "stoppedBy": "targetResults" }"""
        assertEquals(null, parsePlanStreamRecord("done", data, json))
    }

    @Test
    fun `heartbeats and unknown events are ignored`() {
        assertEquals(null, parsePlanStreamRecord("message", "", json))
        assertEquals(null, parsePlanStreamRecord("weird", """{ "x": 1 }""", json))
        assertEquals(null, parsePlanStreamRecord("error", """{ "code": "server", "message": "boom" }""", json))
    }

    @Test
    fun `a malformed chunk or pageInfo is a terminal Decoding failure`() {
        for ((event, data) in listOf(
            "chunk" to """{ "results": [] }""",
            "pageInfo" to """{ "hasNextPage": "yes" }""",
        )) {
            val failure = assertIs<PlanStreamEvent.Failure>(parsePlanStreamRecord(event, data, json))
            assertIs<SpiderError.Decoding>(failure.error)
        }
    }

    @Test
    fun `initial stream body serializes to the plan-stream wire shape`() {
        val body = request.copy(
            destination = Location.Coordinate(49.2, 16.6),
            via = listOf(ViaLocation.PassThrough("1:V")),
        ).toPlanStreamRequest(targetResults = 5, maxWindow = 3.hours, before = null, after = null)
        val expected = json.parseToJsonElement(
            """
            {
              "dateTime": { "earliestDeparture": "2026-07-15T08:00:00Z" },
              "origin": { "location": { "stopLocation": { "stopLocationId": "1:A" } } },
              "destination": { "location": { "coordinate": { "latitude": 49.2, "longitude": 16.6 } } },
              "via": [ { "passThrough": { "stopLocationIds": [ "1:V" ] } } ],
              "targetResults": 5,
              "maxWindow": "PT3H"
            }
            """.trimIndent(),
        )
        assertEquals(expected, json.encodeToJsonElement(PlanStreamRequest.serializer(), body))
    }

    // planStreamNext continues from a prior Done.pageInfo.endCursor: the cursor rides as `after`, and
    // explicitNulls=false omits the unset `before`, so the wire never carries both directions.
    @Test
    fun `planStreamNext continuation sends only after`() {
        val obj = json.encodeToJsonElement(
            PlanStreamRequest.serializer(),
            request.toPlanStreamRequest(targetResults = 5, maxWindow = 3.hours, before = null, after = "c-next"),
        ).jsonObject
        assertEquals("c-next", obj["after"]?.jsonPrimitive?.contentOrNull)
        assertEquals(null, obj["before"])
    }

    // planStreamPrevious continues from a prior Done.pageInfo.startCursor: the cursor rides as `before`,
    // and `after` is omitted.
    @Test
    fun `planStreamPrevious continuation sends only before`() {
        val obj = json.encodeToJsonElement(
            PlanStreamRequest.serializer(),
            request.toPlanStreamRequest(targetResults = 5, maxWindow = 3.hours, before = "c-prev", after = null),
        ).jsonObject
        assertEquals("c-prev", obj["before"]?.jsonPrimitive?.contentOrNull)
        assertEquals(null, obj["after"])
    }

    private val request = PlanRequest(
        origin = Location.Stop("1:A"),
        destination = Location.Stop("1:B"),
        time = RouteTime.DepartAt(Instant.parse("2026-07-15T08:00:00Z")),
    )

    @Test
    fun `stream body always carries targetResults and maxWindow`() {
        val obj = json.encodeToJsonElement(
            PlanStreamRequest.serializer(),
            request.toPlanStreamRequest(targetResults = 3, maxWindow = 2.hours, before = null, after = null),
        ).jsonObject
        assertEquals("3", obj["targetResults"]?.jsonPrimitive?.contentOrNull)
        assertEquals("PT2H", obj["maxWindow"]?.jsonPrimitive?.contentOrNull)
    }

    // Omitted reliability plans on the timetable, so the variable is only sent when set.
    @Test
    fun `stream body carries reliability only when set`() {
        fun reliabilityOf(request: PlanRequest) = json.encodeToJsonElement(
            PlanStreamRequest.serializer(),
            request.toPlanStreamRequest(targetResults = 3, maxWindow = 2.hours, before = null, after = null),
        ).jsonObject["reliability"]?.jsonPrimitive?.contentOrNull

        assertEquals("VERY_SAFE", reliabilityOf(request.copy(reliability = Reliability.VERY_SAFE)))
        assertEquals(null, reliabilityOf(request))
    }

    // 2 hours is the platform minimum in both directions; nothing is widened.
    @Test
    fun `a maxWindow under two hours is rejected with only the field named`() {
        val error = assertFailsWith<SpiderTransportException.BadRequest> {
            request.toPlanStreamRequest(targetResults = 5, maxWindow = 119.minutes, before = null, after = null)
        }
        assertEquals("maxWindow", error.field)
        assertEquals("maxWindow is out of range", error.message)
    }
}
