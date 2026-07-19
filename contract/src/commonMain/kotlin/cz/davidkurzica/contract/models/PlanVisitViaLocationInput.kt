package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanVisitViaLocationInput(
    val coordinate: PlanCoordinateInput? = null,
    val label: String? = null,
    val minimumWaitTime: String? = null,
    val stopLocationIds: List<String>? = null
)
