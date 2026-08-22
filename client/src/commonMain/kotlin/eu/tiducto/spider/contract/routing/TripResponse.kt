package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripResponse(
    val data: TripData? = null,
    val errors: List<GraphQLError>? = null
)
