package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Exactly one of `routes`, `agencies`.
 */
@Serializable
internal data class TransitFilterSelectInput(
    /** Feed-prefixed route ids (`<feedId>:<routeId>`). */
    val routes: List<String>? = null,
    /** Feed-prefixed agency ids (`<feedId>:<agencyId>`). */
    val agencies: List<String>? = null
)
