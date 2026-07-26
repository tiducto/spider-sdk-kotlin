package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanPassThroughViaLocationInput(
    val stopLocationIds: List<String>,
    val label: String? = null
)
