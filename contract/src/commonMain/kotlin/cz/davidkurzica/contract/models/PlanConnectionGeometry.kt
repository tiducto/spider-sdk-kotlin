package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionGeometry(
    val points: String? = null
)
