package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class TripVariables(
    val id: String,
    val serviceDate: String? = null
)
