package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Route(
    val gtfsId: String,
    /** Null when the feed has none. */
    val shortName: String?,
    /** Null when the feed has none. */
    val longName: String?,
    /** Hex without `#`; null when the feed has none. */
    val color: String?,
    /** Hex without `#`; null when the feed has none. */
    val textColor: String?
)
