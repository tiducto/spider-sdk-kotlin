package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class Place(
    val name: String? = null,
    val stop: PlanConnectionStop? = null
)
