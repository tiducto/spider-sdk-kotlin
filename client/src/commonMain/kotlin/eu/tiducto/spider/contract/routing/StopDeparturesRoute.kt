package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesRoute(
    val gtfsId: String,
    val shortName: String? = null,
    val longName: String? = null,
    val mode: TransitMode? = null,
    val color: String? = null,
    val textColor: String? = null
)
