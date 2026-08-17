package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TriangleCyclingFactorsInput(
    val flatness: Double,
    val safety: Double,
    val time: Double
)
