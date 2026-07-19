package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionGeometry(
    val points: String? = null
)
