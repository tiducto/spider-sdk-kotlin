package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanEdge(
    val cursor: String,
    val node: Itinerary
)
