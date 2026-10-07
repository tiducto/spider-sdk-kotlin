package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Place(
    /** The stop's name; `Origin` or `Destination` for a coordinate. */
    val name: String,
    /** Null when the place is not a stop, as for an origin or destination coordinate. */
    val stop: Stop?
)
