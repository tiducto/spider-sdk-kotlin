package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class GraphQLError(
    val message: String,
    val path: List<String>? = null
)
