package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Geometry(
    /** Encoded polyline (precision 1e6). */
    val points: String
)
