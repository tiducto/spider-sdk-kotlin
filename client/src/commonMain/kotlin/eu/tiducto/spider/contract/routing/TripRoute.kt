package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripRoute(
    val shortName: String? = null,
    val longName: String? = null,
    val mode: TransitMode? = null
)
