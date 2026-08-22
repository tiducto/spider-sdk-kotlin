package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanLocationInput(
    val coordinate: PlanCoordinateInput? = null,
    val stopLocation: PlanStopLocationInput? = null
)
