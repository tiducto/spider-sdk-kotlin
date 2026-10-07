package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesRoute(
    val gtfsId: String,
    /** Null when the feed has none. */
    val shortName: String?,
    /** Null when the feed has none. */
    val longName: String?,
    val mode: TransitMode,
    /** Hex without `#`; null when the feed has none. */
    val color: String?,
    /** Hex without `#`; null when the feed has none. */
    val textColor: String?
)
