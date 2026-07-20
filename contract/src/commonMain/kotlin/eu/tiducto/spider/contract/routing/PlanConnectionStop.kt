package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionStop(
    val wheelchairBoarding: WheelchairBoarding? = null
)
