package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class ScooterOptimizationInput(
    val triangle: TriangleScooterFactorsInput? = null,
    val type: ScooterOptimizationType? = null
)
