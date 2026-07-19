package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanLabeledLocationInput(
    val location: PlanLocationInput,
    val label: String? = null
)
