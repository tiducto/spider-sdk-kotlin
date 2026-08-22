package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanDateTimeInput(
    val earliestDeparture: String? = null,
    val latestArrival: String? = null
)
