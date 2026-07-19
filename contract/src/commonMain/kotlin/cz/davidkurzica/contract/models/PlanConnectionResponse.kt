package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionResponse(
    val data: PlanConnectionData? = null,
    val errors: List<GraphQLError>? = null
)
