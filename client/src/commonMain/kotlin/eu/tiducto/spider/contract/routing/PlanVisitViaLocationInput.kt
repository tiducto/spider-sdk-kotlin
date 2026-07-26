package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanVisitViaLocationInput(
    val coordinate: PlanCoordinateInput? = null,
    val label: String? = null,
    val minimumWaitTime: String? = null,
    val stopLocationIds: List<String>? = null
)
