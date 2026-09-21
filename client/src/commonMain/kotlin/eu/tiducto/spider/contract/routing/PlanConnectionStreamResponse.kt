package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionStreamResponse(
    val data: PlanConnectionStreamData? = null,
    val errors: List<GraphQLError>? = null
)
