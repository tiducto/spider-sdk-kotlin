package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanModesInput(
    val direct: List<PlanDirectMode>? = null,
    val directOnly: Boolean? = null,
    val transit: PlanTransitModesInput? = null,
    val transitOnly: Boolean? = null
)
