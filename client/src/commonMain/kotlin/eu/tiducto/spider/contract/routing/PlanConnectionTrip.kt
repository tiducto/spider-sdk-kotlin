package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionTrip(
    val gtfsId: String,
    val bikesAllowed: BikesAllowed? = null
)
