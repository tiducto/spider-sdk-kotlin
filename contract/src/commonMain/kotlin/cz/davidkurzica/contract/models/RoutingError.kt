package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class RoutingError(
    val code: RoutingErrorCode,
    val description: String,
    val inputField: InputField? = null
)
