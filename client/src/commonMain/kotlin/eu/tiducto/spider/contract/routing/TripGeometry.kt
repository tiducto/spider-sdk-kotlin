package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripGeometry(
    /** Encoded polyline (precision 1e6). */
    val points: String,
    /** Number of points. */
    val length: Int
)
