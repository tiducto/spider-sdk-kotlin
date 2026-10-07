package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripStop(
    val gtfsId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val wheelchairBoarding: WheelchairBoarding,
    /** Null when the feed has none. */
    val platformCode: String?,
    /** Null when the feed has none. */
    val zoneId: String?
)
