package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Place(
    val name: String? = null,
    /** Null when the place is not a stop, as for an origin or destination coordinate. */
    val stop: Stop? = null
)
