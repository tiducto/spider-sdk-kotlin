package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class TripStop(
    val gtfsId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val wheelchairBoarding: WheelchairBoarding? = null
)
