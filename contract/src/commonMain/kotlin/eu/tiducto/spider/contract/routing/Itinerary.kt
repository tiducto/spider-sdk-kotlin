package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class Itinerary(
    val numberOfTransfers: Int,
    val legs: List<Leg>,
    val start: String? = null,
    val end: String? = null,
    val duration: Long? = null,
    val waitingTime: Long? = null,
    val accessibilityScore: Double? = null
)
