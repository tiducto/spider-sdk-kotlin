package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripStop(
    val gtfsId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val wheelchairBoarding: WheelchairBoarding? = null,
    /** Null when the feed has none. */
    val platformCode: String? = null,
    /** Null when the feed has none. */
    val zoneId: String? = null
)
