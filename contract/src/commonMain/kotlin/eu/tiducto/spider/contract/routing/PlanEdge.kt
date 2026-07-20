package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanEdge(
    val cursor: String,
    val node: Itinerary
)
