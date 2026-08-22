package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesVariables(
    val id: String,
    val numberOfDepartures: Int? = null,
    val startTime: Long? = null,
    val timeRange: Int? = null
)
