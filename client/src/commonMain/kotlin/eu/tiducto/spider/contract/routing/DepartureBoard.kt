package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Departures board for one platform (a stop id) or a whole station (a station id), realtime merged in. A trip's final stop, a canceled departure and a departure that does not allow boarding give no row.
 */
@Serializable
internal data class DepartureBoard(
    val gtfsId: String,
    val name: String,
    /** Null on a station board. */
    val wheelchairBoarding: WheelchairBoarding?,
    val stoptimesWithoutPatterns: List<StopDeparturesStoptime>
)
