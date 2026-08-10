package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripGeometry(
    val points: String? = null,
    val length: Int? = null
)
