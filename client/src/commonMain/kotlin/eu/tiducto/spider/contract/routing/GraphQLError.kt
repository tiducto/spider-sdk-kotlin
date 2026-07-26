package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class GraphQLError(
    val message: String,
    val path: List<String>? = null
)
