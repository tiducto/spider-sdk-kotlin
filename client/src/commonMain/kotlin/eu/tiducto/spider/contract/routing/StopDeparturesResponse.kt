package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesResponse(
    val data: StopDeparturesData? = null,
    val errors: List<GraphQLError>? = null
)
