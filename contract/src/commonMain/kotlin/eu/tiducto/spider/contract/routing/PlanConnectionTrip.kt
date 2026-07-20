package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionTrip(
    val gtfsId: String,
    val bikesAllowed: BikesAllowed? = null
)
