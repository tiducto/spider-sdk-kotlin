package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesRoute(
    val shortName: String? = null,
    val longName: String? = null,
    val mode: TransitMode? = null
)
