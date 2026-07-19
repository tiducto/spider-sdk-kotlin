package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionRoute(
    val shortName: String? = null,
    val longName: String? = null
)
