package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Place(
    /** The stop's name; `Origin` or `Destination` for a coordinate. */
    val name: String,
    /** The position in the leg's trip of this stop time, as the trip's `stopPosition`. Null unless the leg is a ride. */
    val stopPosition: Int?,
    /** Null when the place is not a stop, as for an origin or destination coordinate. */
    val stop: Stop?
)
