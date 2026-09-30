package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Route(
    val gtfsId: String,
    val shortName: String? = null,
    val longName: String? = null,
    val color: String? = null,
    val textColor: String? = null
)
