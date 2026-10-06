package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Departures board for one platform (a stop id) or a whole station (a station id), realtime merged in. A trip's final stop gives no row, since nobody boards there.
 */
@Serializable
internal data class DepartureBoard(
    val gtfsId: String,
    val name: String,
    /** Null on a station board. */
    val wheelchairBoarding: WheelchairBoarding? = null,
    val stoptimesWithoutPatterns: List<StopDeparturesStoptime>? = null
)
