package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Standard error body: a stable machine-readable `code` and a human-readable `message`.
 */
@Serializable
internal data class ErrorResponse(
    val message: String,
    val code: String? = null
)
