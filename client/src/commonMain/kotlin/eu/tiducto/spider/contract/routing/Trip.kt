package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Trip(
    val gtfsId: String,
    val route: Route,
    val bikesAllowed: BikesAllowed? = null
)
