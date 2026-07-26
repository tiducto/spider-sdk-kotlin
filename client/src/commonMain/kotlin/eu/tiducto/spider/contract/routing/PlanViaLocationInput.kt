package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanViaLocationInput(
    val passThrough: PlanPassThroughViaLocationInput? = null,
    val visit: PlanVisitViaLocationInput? = null
)
