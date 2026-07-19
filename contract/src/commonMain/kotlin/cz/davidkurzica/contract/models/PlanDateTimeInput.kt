package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanDateTimeInput(
    val earliestDeparture: String? = null,
    val latestArrival: String? = null
)
