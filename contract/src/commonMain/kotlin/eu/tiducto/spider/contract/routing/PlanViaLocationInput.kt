package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanViaLocationInput(
    val passThrough: PlanPassThroughViaLocationInput? = null,
    val visit: PlanVisitViaLocationInput? = null
)
