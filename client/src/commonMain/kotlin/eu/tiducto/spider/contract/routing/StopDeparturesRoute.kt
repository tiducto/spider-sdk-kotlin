package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesRoute(
    val gtfsId: String,
    val shortName: String? = null,
    val longName: String? = null,
    val mode: TransitMode? = null,
    /** Hex without `#`; null when the feed has none. */
    val color: String? = null,
    /** Hex without `#`; null when the feed has none. */
    val textColor: String? = null
)
