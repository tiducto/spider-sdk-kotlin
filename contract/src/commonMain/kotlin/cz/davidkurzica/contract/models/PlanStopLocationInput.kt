package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanStopLocationInput(
    val stopLocationId: String
)
