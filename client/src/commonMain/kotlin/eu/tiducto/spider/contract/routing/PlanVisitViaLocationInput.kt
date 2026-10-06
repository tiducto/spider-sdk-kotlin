package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * A location the journey stops at.
 */
@Serializable
internal data class PlanVisitViaLocationInput(
    /** 1 to 10 feed-prefixed stop or station ids; visiting any one of them is enough. Absent, empty, or more than 10 is a 400 naming `via`. */
    val stopLocationIds: List<String>? = null,
    /** Least time to stay at the location, as an ISO-8601 duration: `PT0S` to `PT1H`; rejected, never clamped. Absent means `PT0S`. */
    val minimumWaitTime: String? = null
)
