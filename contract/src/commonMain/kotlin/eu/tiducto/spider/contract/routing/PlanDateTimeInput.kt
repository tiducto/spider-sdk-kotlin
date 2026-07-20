package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanDateTimeInput(
    val earliestDeparture: String? = null,
    val latestArrival: String? = null
)
