package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanEdge(
    val cursor: String,
    val node: Itinerary
)
