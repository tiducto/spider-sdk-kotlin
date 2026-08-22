package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Route(
    val shortName: String? = null,
    val longName: String? = null
)
