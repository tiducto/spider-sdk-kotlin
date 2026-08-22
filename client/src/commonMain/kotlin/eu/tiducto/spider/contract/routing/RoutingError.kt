package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class RoutingError(
    val code: RoutingErrorCode,
    val description: String,
    val inputField: InputField? = null
)
