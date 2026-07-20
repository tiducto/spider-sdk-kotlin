package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class StopDeparturesResponse(
    val data: StopDeparturesData? = null,
    val errors: List<GraphQLError>? = null
)
