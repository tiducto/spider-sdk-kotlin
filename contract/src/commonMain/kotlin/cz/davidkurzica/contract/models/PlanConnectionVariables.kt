package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionVariables(
    val dateTime: PlanDateTimeInput,
    val origin: PlanLabeledLocationInput,
    val destination: PlanLabeledLocationInput,
    val via: List<PlanViaLocationInput>? = null,
    val first: Int? = null,
    val before: String? = null,
    val after: String? = null
)
