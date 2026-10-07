package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesTrip(
    val gtfsId: String,
    val bikesAllowed: BikesAllowed,
    val wheelchairAccessible: WheelchairBoarding,
    val route: StopDeparturesRoute
)
