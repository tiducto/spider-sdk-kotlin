package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class StopDeparturesData(
    val asStop: Stop? = null,
    val asStation: Stop? = null
)
