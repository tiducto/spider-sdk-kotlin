package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanCoordinateInput(
    val latitude: Double,
    val longitude: Double
)
