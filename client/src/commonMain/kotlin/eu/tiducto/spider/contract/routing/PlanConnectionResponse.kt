package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionResponse(
    val data: PlanConnectionData? = null,
    val errors: List<GraphQLError>? = null
)
