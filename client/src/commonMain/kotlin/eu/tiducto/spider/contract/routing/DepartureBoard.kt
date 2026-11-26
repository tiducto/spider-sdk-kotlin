package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Departures for one stop (a stop id) or a whole station (a station id), realtime merged in. A trip's final stop, a canceled departure and a departure that does not allow boarding give no row.
 */
@Serializable
internal data class DepartureBoard(
    val gtfsId: String,
    val name: String,
    /** Null for a station's departures. */
    val wheelchairBoarding: WheelchairBoarding?,
    /** The station the stop belongs to, as OTP's `Stop.parentStation`; its `gtfsId` asks for the whole station's departures. Null for a station's departures and for a stop without one. */
    val parentStation: ParentStation?,
    val stoptimesWithoutPatterns: List<StopDeparturesStoptime>
)
