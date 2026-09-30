package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesData(
    val asStop: StopDeparturesStop2? = null,
    val asStation: StopDeparturesStop2? = null
)
