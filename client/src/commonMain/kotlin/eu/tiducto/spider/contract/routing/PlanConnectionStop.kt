package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionStop(
    val gtfsId: String,
    val wheelchairBoarding: WheelchairBoarding? = null
)
