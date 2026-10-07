package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesStop(
    val gtfsId: String,
    /** Null when the feed has none. */
    val platformCode: String?
)
