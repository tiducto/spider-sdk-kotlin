package eu.tiducto.spider.client

import eu.tiducto.spider.contract.realtime.AlertsResponseDto
import eu.tiducto.spider.contract.realtime.DelaysResponseDto
import eu.tiducto.spider.contract.realtime.VehicleByTripResponseDto
import eu.tiducto.spider.contract.realtime.VehiclesResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Guards the Realtime (GTFS-RT) wire format. There is no request body (plain GETs), so this pins the
 * response shapes: partial/absent fields decode, extra fields are tolerated, and the enum-ish strings
 * pass through verbatim (a producer's newer value must survive, never throw or get rewritten). Also pins
 * the domain OccupancyStatus tolerance, the one realtime enum the SDK maps rather than passes through.
 *
 * Json config mirrors RealtimeClient's (`ignoreUnknownKeys = true`). Native-safe test names.
 */
class RealtimeWireContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `vehicles response decodes and tolerates a partial vehicle plus extra fields`() {
        // `provider` is an unmodeled extra; most vehicle fields are absent (a normal partial feed).
        val body =
            """
            {
              "vehicles": [
                {"tripId":"1:T1","latitude":49.2,"longitude":16.6,"currentStatus":"IN_TRANSIT_TO",
                 "occupancyStatus":"MANY_SEATS_AVAILABLE","provider":"idsjmk"},
                {"tripId":"1:T2"}
              ],
              "missing": ["1:T3"],
              "feedTimestamp": 1721385600,
              "staleSeconds": 4
            }
            """.trimIndent()

        val dto = json.decodeFromString(VehiclesResponseDto.serializer(), body)
        assertEquals(2, dto.vehicles.size)
        assertEquals(listOf("1:T3"), dto.missing)
        assertEquals(1721385600L, dto.feedTimestamp)
        assertEquals("IN_TRANSIT_TO", dto.vehicles[0].currentStatus)
        assertNull(dto.vehicles[1].latitude)
    }

    @Test
    fun `an unknown enum-ish string passes through unchanged instead of throwing`() {
        // A currentStatus value not in the GTFS-RT vocabulary this SDK version knows must still decode.
        val body = """{"vehicles":[{"tripId":"1:T1","currentStatus":"TELEPORTING"}]}"""
        val dto = json.decodeFromString(VehiclesResponseDto.serializer(), body)
        assertEquals("TELEPORTING", dto.vehicles.single().currentStatus)
    }

    @Test
    fun `by-trip with no vehicle decodes to a null vehicle`() {
        val dto = json.decodeFromString(
            VehicleByTripResponseDto.serializer(),
            """{"vehicle":null,"feedTimestamp":null,"staleSeconds":null}""",
        )
        assertNull(dto.vehicle)
        assertNull(dto.feedTimestamp)
    }

    @Test
    fun `delays response decodes nested stop-time updates`() {
        val body =
            """
            {"delays":[{"tripId":"1:T1","delaySeconds":90,"scheduleRelationship":"SCHEDULED",
              "stopTimeUpdates":[{"stopId":"1:S1","departureDelay":90}]}],
             "missing":[],"feedTimestamp":1721385600}
            """.trimIndent()
        val dto = json.decodeFromString(DelaysResponseDto.serializer(), body)
        val delay = dto.delays.single()
        assertEquals(90, delay.delaySeconds)
        assertEquals("1:S1", delay.stopTimeUpdates.single().stopId)
    }

    @Test
    fun `alerts response decodes with active periods and informed entities`() {
        val body =
            """
            {"alerts":[{"id":"a1","cause":"CONSTRUCTION","effect":"DETOUR","severityLevel":"WARNING",
              "headerText":"Detour","activePeriods":[{"start":1721385600,"end":1721389200}],
              "informedEntities":[{"routeId":"1:R1"}]}]}
            """.trimIndent()
        val dto = json.decodeFromString(AlertsResponseDto.serializer(), body)
        val alert = dto.alerts.single()
        assertEquals("CONSTRUCTION", alert.cause)
        assertEquals(1721385600L, alert.activePeriods.single().start)
        assertEquals("1:R1", alert.informedEntities.single().routeId)
        assertNull(dto.feedTimestamp)
    }

    @Test
    fun `occupancy status maps known values and hides not-reported and keeps unknowns visible`() {
        assertEquals(OccupancyStatus.FULL, OccupancyStatus.fromWire("FULL"))
        assertNull(OccupancyStatus.fromWire("NO_DATA_AVAILABLE"))
        assertNull(OccupancyStatus.fromWire(null))
        assertEquals(OccupancyStatus.UNKNOWN, OccupancyStatus.fromWire("SOMETHING_NEW"))
        assertTrue(OccupancyStatus.UNKNOWN in OccupancyStatus.entries)
    }
}
