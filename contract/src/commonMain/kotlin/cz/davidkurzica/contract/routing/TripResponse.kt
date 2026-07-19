package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class TripResponse(
    val data: TripData? = null,
    val errors: List<GraphQLError>? = null
)
