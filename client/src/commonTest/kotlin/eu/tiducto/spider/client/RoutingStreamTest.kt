package eu.tiducto.spider.client

import eu.tiducto.spider.contract.routing.PlanConnectionStreamVariables
import eu.tiducto.spider.contract.routing.PlanCoordinateInput
import eu.tiducto.spider.contract.routing.PlanDateTimeInput
import eu.tiducto.spider.contract.routing.PlanLabeledLocationInput
import eu.tiducto.spider.contract.routing.PlanLocationInput
import eu.tiducto.spider.contract.routing.PlanPassThroughViaLocationInput
import eu.tiducto.spider.contract.routing.PlanStopLocationInput
import eu.tiducto.spider.contract.routing.PlanViaLocationInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

/**
 * Guards the SSE `plan-stream` handling: the record parser that turns `chunk`/`pageInfo`/`done`/`error`
 * events into [PlanStreamEvent]s (including realtime-delay mapping onto legs), and the stream request's
 * wire shape. The Json config mirrors RoutingClient's exactly — the two must stay in lockstep.
 */
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
              "frontier": 1800, "found": 3, "finalized": 1,
              "results": [
                {
                  "numberOfTransfers": 1,
                  "start": "2026-07-15T08:00:00Z", "end": "2026-07-15T08:30:00Z", "duration": 1800,
                  "legs": [
                    {
                      "mode": "BUS",
                      "start": { "scheduledTime": "2026-07-15T08:00:00Z", "estimated": { "time": "2026-07-15T08:01:00Z", "delay": "PT60S" } },
                      "end":   { "scheduledTime": "2026-07-15T08:30:00Z", "estimated": { "time": "2026-07-15T08:32:00Z", "delay": "PT120S" } },
                      "realtimeState": "UPDATED", "realTime": true,
                      "from": { "name": "Origin", "stop": { "gtfsId": "1:A" } },
                      "to":   { "name": "Dest",   "stop": { "gtfsId": "1:B" } },
                      "route": { "shortName": "12" }, "trip": { "gtfsId": "1:T" }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val event = assertIs<PlanStreamEvent.Chunk>(parsePlanStreamRecord("chunk", data, json))
        assertEquals(1800L, event.frontierSeconds)
        assertEquals(3, event.found)
        assertEquals(1, event.finalized)

        val itinerary = event.itineraries.single()
        assertEquals(1, itinerary.numberOfTransfers)
        assertEquals(1800L, itinerary.durationSeconds)

        val leg = itinerary.legs.single()
        assertEquals(TransitMode.BUS, leg.mode)
        assertEquals(60.seconds, leg.startDelay)
        assertEquals(120.seconds, leg.endDelay)
        assertEquals("2026-07-15T08:01:00Z", leg.startEstimated)
        assertEquals(true, leg.isRealtime)
        assertEquals("UPDATED", leg.realtimeState)
        assertEquals("1:A", leg.fromGtfsId)
        assertEquals("1:B", leg.toGtfsId)
    }

    @Test
    fun `pageInfo maps to continuation cursors`() {
        val data = """{ "startCursor": "c-prev", "endCursor": "c-next", "hasNextPage": true, "hasPreviousPage": false, "searchWindowUsed": "PT1H" }"""
        val event = assertIs<PlanStreamEvent.Page>(parsePlanStreamRecord("pageInfo", data, json))
        assertEquals("c-prev", event.pageInfo.startCursor)
        assertEquals("c-next", event.pageInfo.endCursor)
        assertEquals(true, event.pageInfo.hasNextPage)
        assertEquals(false, event.pageInfo.hasPreviousPage)
        assertEquals("PT1H", event.pageInfo.searchWindowUsed)
    }

    @Test
    fun `done maps to the terminal summary`() {
        val data = """{ "iterations": 3, "windowSeconds": 3600, "resultCount": 5, "stoppedBy": "targetResults" }"""
        val event = assertIs<PlanStreamEvent.Done>(parsePlanStreamRecord("done", data, json))
        assertEquals(3, event.iterations)
        assertEquals(3600L, event.windowSeconds)
        assertEquals(5, event.resultCount)
        assertEquals("targetResults", event.stoppedBy)
    }

    // A stream `error` record is the GraphQL error envelope; a top-level BAD_REQUEST becomes a typed BadRequest.
    @Test
    fun `error event maps to a typed BadRequest failure`() {
        val data = """{ "data": null, "errors": [ { "message": "searchWindow exceeds the cap", "extensions": { "code": "BAD_REQUEST", "field": "searchWindow" } } ] }"""
        val event = assertIs<PlanStreamEvent.Failure>(parsePlanStreamRecord("error", data, json))
        val error = assertIs<SpiderError.BadRequest>(event.error)
        assertEquals("searchWindow", error.field)
        assertEquals("searchWindow exceeds the cap", error.message)
    }

    @Test
    fun `heartbeats and unknown events are ignored`() {
        assertEquals(null, parsePlanStreamRecord("message", "", json))
        assertEquals(null, parsePlanStreamRecord("weird", """{ "x": 1 }""", json))
    }

    // Pins the stream request wire shape (targetResults/maxWindow + via) so a contract regen can't silently
    // rename or reorder the fields the SDK sends to /routing/plan-stream.
    @Test
    fun `stream variables serialize to the plan-stream wire shape`() {
        val variables = PlanConnectionStreamVariables(
            dateTime = PlanDateTimeInput(earliestDeparture = "2026-07-15T08:00:00Z"),
            origin = PlanLabeledLocationInput(
                location = PlanLocationInput(stopLocation = PlanStopLocationInput(stopLocationId = "1:A")),
            ),
            destination = PlanLabeledLocationInput(
                location = PlanLocationInput(coordinate = PlanCoordinateInput(latitude = 49.2, longitude = 16.6)),
            ),
            via = listOf(PlanViaLocationInput(passThrough = PlanPassThroughViaLocationInput(stopLocationIds = listOf("1:V")))),
            targetResults = 5,
            maxWindow = "PT3H",
        )
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
        val actual = json.encodeToJsonElement(PlanConnectionStreamVariables.serializer(), variables)
        assertEquals(expected, actual)
    }
}
