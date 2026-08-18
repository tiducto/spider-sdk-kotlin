package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesData(
    val asStop: StopDeparturesStop? = null,
    val asStation: StopDeparturesStop? = null
)
