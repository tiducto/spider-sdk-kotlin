package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripGeometry(
    /** Encoded polyline (precision 1e5). */
    val points: String? = null,
    /** Number of points. */
    val length: Int? = null
)
