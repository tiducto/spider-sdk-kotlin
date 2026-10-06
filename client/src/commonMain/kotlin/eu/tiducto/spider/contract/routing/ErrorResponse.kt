package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Error body: a stable machine-readable `code` (a state name) and a human-readable `message`.
 */
@Serializable
internal data class ErrorResponse(
    /** For bad_request: `<field> is required|invalid|out of range|not allowed`. */
    val message: String,
    val code: String? = null,
    /** The request member a 400 names, as a dot path from the body root (array positions omitted). */
    val field: String? = null
)
