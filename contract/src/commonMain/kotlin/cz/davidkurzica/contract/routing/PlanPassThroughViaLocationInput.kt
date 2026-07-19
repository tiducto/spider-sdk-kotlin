package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanPassThroughViaLocationInput(
    val stopLocationIds: List<String>,
    val label: String? = null
)
