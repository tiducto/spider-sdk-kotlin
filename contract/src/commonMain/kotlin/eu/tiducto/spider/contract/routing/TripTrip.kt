package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class TripTrip(
    val gtfsId: String,
    val route: Route,
    val directionId: String? = null,
    val tripHeadsign: String? = null,
    val bikesAllowed: BikesAllowed? = null,
    val stoptimesForDate: List<TripStoptime>? = null,
    val tripGeometry: Geometry? = null
)
