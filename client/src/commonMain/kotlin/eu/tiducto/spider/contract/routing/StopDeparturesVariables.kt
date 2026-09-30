package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesVariables(
    val id: String,
    val numberOfDepartures: Int,
    val timeRange: Int,
    val startTime: Long? = null
)
