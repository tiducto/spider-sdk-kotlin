package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripTrip(
    val gtfsId: String,
    val route: TripRoute,
    val directionId: String? = null,
    val tripHeadsign: String? = null,
    val bikesAllowed: BikesAllowed? = null,
    val wheelchairAccessible: WheelchairBoarding? = null,
    val stoptimesForDate: List<Stoptime>? = null,
    val tripGeometry: TripGeometry? = null
)
