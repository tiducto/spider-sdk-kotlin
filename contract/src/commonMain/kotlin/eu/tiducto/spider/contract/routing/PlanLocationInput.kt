package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanLocationInput(
    val coordinate: PlanCoordinateInput? = null,
    val stopLocation: PlanStopLocationInput? = null
)
