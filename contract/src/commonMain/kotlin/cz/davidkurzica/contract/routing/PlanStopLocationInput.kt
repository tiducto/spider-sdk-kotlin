package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanStopLocationInput(
    val stopLocationId: String
)
