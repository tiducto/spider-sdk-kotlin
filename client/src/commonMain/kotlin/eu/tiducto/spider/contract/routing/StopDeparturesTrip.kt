package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesTrip(
    val gtfsId: String,
    val route: StopDeparturesRoute,
    val bikesAllowed: BikesAllowed? = null
)
