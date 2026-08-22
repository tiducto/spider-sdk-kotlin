package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class CyclingOptimizationInput(
    val triangle: TriangleCyclingFactorsInput? = null,
    val type: CyclingOptimizationType? = null
)
