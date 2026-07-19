package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionResponse(
    val data: PlanConnectionData? = null,
    val errors: List<GraphQLError>? = null
)
