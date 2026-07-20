package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val gtfsId: String,
    val route: Route,
    val bikesAllowed: BikesAllowed? = null
)
