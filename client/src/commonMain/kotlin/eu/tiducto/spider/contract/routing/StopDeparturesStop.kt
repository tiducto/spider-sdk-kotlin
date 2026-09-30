package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesStop(
    val gtfsId: String,
    val platformCode: String? = null
)
